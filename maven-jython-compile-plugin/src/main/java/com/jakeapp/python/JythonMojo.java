package com.jakeapp.python;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which generates jython-standalone into .
 * 
 * @goal jython
 * 
 * @phase compile
 */
public class JythonMojo extends AbstractMojo {
	/**
	 * @parameter expression="${project.build.testOutputDirectory}"
	 * @required
	 */
	private File testOutputDirectory;
	/**
	 * @parameter expression="${project.build.outputDirectory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * @parameter expression="${project.build.scriptSourceDirectory}"
	 * @required
	 */
	private File scriptDirectory;

	/**
	 * Executable program to run for test.
	 * 
	 * @parameter expression="${jython.libraries}"
	 * @optional
	 */
	private List<String> libraries;

	/**
	 * Cache to download and build python packages
	 * 
	 * @parameter expression="${jython.temporaryDirectory}"
	 *            default="target/jython-plugins-tmp"
	 * @optional
	 */
	private File temporaryBuildDirectory;

	/**
	 * Dependencies.
	 * 
	 * @parameter expression="${plugin.artifacts}"
	 */
	private List<DefaultArtifact> pluginArtifacts;

	/**
	 * Include jython
	 * 
	 * @parameter expression="${jython.includeJython}" default="false"
	 */
	private boolean includeJython;
	private File jythonFakeExecutable;

	public void execute() throws MojoExecutionException {
		if (temporaryBuildDirectory == null) {
			temporaryBuildDirectory = new File("target/jython-plugins-tmp");
		}
		temporaryBuildDirectory.mkdirs();

		// all we have to do is to run nose on the source directory
		/**
		 * Strategy A: include jython in plugin. Extract on the run
		 * 
		 * Strategy B: Project also has dependency on jython. We find that jar
		 * and extract it and work from there. B has the benefit that we don't
		 * have to update this plugin for every version and the user needs the
		 * jython dependency anyway to call the Python Console
		 * 
		 */
		DefaultArtifact artifact = getJython();
		if (!artifact.getFile().getName().endsWith(".jar")) {
			throw new MojoExecutionException("I expected " + artifact
					+ " to provide a jar, but got " + artifact.getFile());
		}
		waitForUser();

		Collection<File> jythonFiles = extractJarToDirectory(
				artifact.getFile(), outputDirectory);
		jythonFakeExecutable = new File(outputDirectory, "jython");
		try {
			jythonFakeExecutable.createNewFile();
		} catch (IOException e) {
			throw new MojoExecutionException("couldn't create file", e);
		}

		waitForUser();

		// now what? we have the jython content, now we need
		// easy_install
		getLog().info("installing easy_install ...");
		URL res = getClass().getResource("setuptools-0.6c11.jar");
		if (res == null)
			throw new MojoExecutionException(
					"resource setuptools-0.6c11.jar not found");
		File setuptoolsJar = new File(outputDirectory, "setuptools.jar");
		try {
			FileUtils.copyInputStreamToFile(res.openStream(), setuptoolsJar);
		} catch (IOException e) {
			throw new MojoExecutionException("copying setuptools failed", e);
		}
		extractJarToDirectory(setuptoolsJar, new File(outputDirectory, "Lib"));
		getLog().info("installing easy_install done");

		waitForUser();

		getLog().info("installing requested libraries");
		// then we need to call easy_install to install the other dependencies.
		runJythonScriptOnInstall(outputDirectory, getEasyInstallArgs());
		getLog().info("installing requested libraries done");

		waitForUser();

		if (!includeJython) {
			for (File f : jythonFiles) {
				f.delete();
			}
		}

		/*
		 * If the project does not want its python sources to be in Lib/
		 * it needs to call 
		 * 
		 * PySystemState.addPaths(path, jarFileName + "/myLibFolder");
		 * 
		 * before starting Python up.
		 */
	}

	private void waitForUser() {
		try {
			System.out.print("press a key to continue: ");
			System.out.flush();
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<String> getEasyInstallArgs() {
		List<String> args = new ArrayList<String>();

		// I want to launch
		args.add("java");
		// to run the generated jython installation here
		args.add("-cp");
		args.add("." + getClassPathSeparator() + "Lib");
		// which should know about itself
		args.add("-Dpython.home=" + outputDirectory.getAbsolutePath());
		args.add("-Dpython.executable="
				+ jythonFakeExecutable.getAbsolutePath());
		args.add("org.python.util.jython");
		// and it should run easy_install
		args.add("Lib/easy_install.py");
		// with some arguments
		args.add("--optimize");
		// and cache here
		args.add("--build-directory");
		args.add(temporaryBuildDirectory.getAbsolutePath());
		// and install these libraries
		args.addAll(libraries);

		return args;
	}

	private String getClassPathSeparator() {
		if (File.separatorChar == '\\')
			return ";";
		else
			return ":";
	}

	public void runJythonScriptOnInstall(File outputDirectory, List<String> args)
			throws MojoExecutionException {
		getLog().info("running " + args + " in " + outputDirectory);
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.directory(outputDirectory);
		final Process p;
		try {
			p = pb.start();
		} catch (IOException e) {
			throw new MojoExecutionException(
					"Executing jython failed. tried to run: " + pb.command(), e);
		}
		copyIO(p.getInputStream(), System.out);
		copyIO(p.getErrorStream(), System.err);
		copyIO(System.in, p.getOutputStream());
		try {
			if (p.waitFor() != 0) {
				throw new MojoExecutionException(
						"Jython failed with return code: " + p.exitValue());
			}
		} catch (InterruptedException e) {
			throw new MojoExecutionException("Python tests were interrupted", e);
		}

	}

	public Collection<File> extractJarToDirectory(File jar, File outputDirectory)
			throws MojoExecutionException {
		getLog().info("extracting " + jar);
		JarFile ja = openJarFile(jar);
		Enumeration<JarEntry> en = ja.entries();
		Collection<File> files = extractAllFiles(outputDirectory, ja, en);
		closeFile(ja);
		return files;
	}

	private JarFile openJarFile(File jar) throws MojoExecutionException {
		try {
			return new JarFile(jar);
		} catch (IOException e) {
			throw new MojoExecutionException(
					"opening jython artifact jar failed", e);
		}
	}

	private void closeFile(ZipFile ja) throws MojoExecutionException {
		try {
			ja.close();
		} catch (IOException e) {
			throw new MojoExecutionException(
					"closing jython artifact jar failed", e);
		}
	}

	private Collection<File> extractAllFiles(File outputDirectory, ZipFile ja,
			Enumeration<JarEntry> en) throws MojoExecutionException {
		List<File> files = new ArrayList<File>();
		while (en.hasMoreElements()) {
			JarEntry el = en.nextElement();
			// getLog().info(" > " + el);
			if (!el.isDirectory()) {
				File destFile = new File(outputDirectory, el.getName());
				// destFile = new File(outputDirectory, destFile.getName());
				destFile.getParentFile().mkdirs();
				try {
					IOUtils.copy(ja.getInputStream(el), new FileOutputStream(
							destFile));
				} catch (IOException e) {
					throw new MojoExecutionException(
							"extracting " + el.getName()
									+ " from jython artifact jar failed", e);
				}
				files.add(destFile);
			}
		}
		return files;
	}

	private DefaultArtifact getJython() throws MojoExecutionException {
		for (DefaultArtifact i : pluginArtifacts) {
			if (i.getArtifactId().equals("jython-standalone")
					&& i.getGroupId().equals("org.python")) {
				return i;
			}
		}
		throw new MojoExecutionException(
				"org.python.jython-standalone dependency not found. "
						+ "\n"
						+ "Add a dependency to jython-standalone to your project: \n"
						+ "	<dependency>\n"
						+ "		<groupId>org.python</groupId>\n"
						+ "		<artifactId>jython-standalone</artifactId>\n"
						+ "		<version>2.5.2</version>\n" + "	</dependency>"
						+ "\n");
	}

	private void copyIO(final InputStream input, final OutputStream output) {
		new Thread(new Runnable() {
			public void run() {
				try {
					IOUtils.copy(input, output);
				} catch (IOException e) {
					getLog().error(e);
				}
			}
		}).start();

	}
}
