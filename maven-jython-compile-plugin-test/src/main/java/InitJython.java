import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyFile;
import org.python.core.PySystemState;
import org.python.core.imp;
import org.python.util.InteractiveConsole;
import org.python.util.JLineConsole;

/**
 * The point of all the jython install madness is that we can call jython now.
 * 
 * So we want:
 * 
 * Java initialization class (this) --&gt; Jython/Python installation --&gt;
 * Python script --&gt; uses some Java code
 * 
 * @author user
 * 
 */
public class InitJython {
	private static InteractiveConsole newInterpreter(boolean interactiveStdin) {
		if (!interactiveStdin) {
			return new InteractiveConsole();
		}

		String interpClass = PySystemState.registry.getProperty(
				"python.console", "");
		if (interpClass.length() > 0) {
			try {
				return (InteractiveConsole) Class.forName(interpClass)
						.newInstance();
			} catch (Throwable t) {
				// fall through
			}
		}
		return new JLineConsole();
	}

	public static void main(String[] args) throws PyException {
		System.out.println("Java started.");

		System.out.println("Setting python home.");
		if (System.getProperty("python.home") == null) {
			URL dirURL = InitJython.class.getClassLoader().getResource(".");
			if (dirURL != null && dirURL.getProtocol().equals("file")) {
				try {
					System.setProperty("python.home",
							new File(dirURL.toURI()).getPath());
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			} else {
				System.err.println("URL for . is " + dirURL);
			}
		}

		PySystemState.initialize(PySystemState.getBaseProperties(),
				new Properties(), args);

		PySystemState systemState = Py.getSystemState();

		// Decide if stdin is interactive
		boolean interactive = ((PyFile) Py.defaultSystemState.stdin).isatty();
		if (!interactive) {
			systemState.ps1 = systemState.ps2 = Py.EmptyString;
		}

		// Now create an interpreter
		InteractiveConsole interp = newInterpreter(interactive);
		systemState.__setattr__("_jy_interpreter", Py.java2py(interp));

		imp.load("site");

		InteractiveConsole c = interp;
		System.out.println(args.length + "Arguments: ");
		for (String s : args) {
			System.out.print(s);
			System.out.print(", ");
		}
		System.out.println();

		if (args.length > 0) {
			if (args[0].equals("eval"))
				if (args.length > 1)
					c.eval(args[1]);
				else
					c.eval("try:\n import fibcalc\n fibcalc.main()\nexcept SystemExit: pass");
			if (args[0].equals("run"))
				if (args.length > 1)
					c.execfile(args[1]);
				else
					c.execfile(InitJython.class
							.getResourceAsStream("Lib/fibcalc/__init__.py"),
							"fibcalc/__init__.py");
			else
				System.out.println("use either eval or run as first argument");
		} else
			c.interact();
		System.out.println("Java exiting.");
	}
}
