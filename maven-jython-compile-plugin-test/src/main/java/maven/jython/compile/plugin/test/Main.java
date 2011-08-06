package maven.jython.compile.plugin.test;

import org.python.util.InteractiveConsole;

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
public class Main {

	public static void main(String[] args) {
		System.out.println("Java started.");

		InteractiveConsole c = new InteractiveConsole();
		c.eval("import fibcalc\n" + "fibcalc.main()");
		System.out.println("Java exiting.");
	}
}
