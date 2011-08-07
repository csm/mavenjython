
print "Python started."

from maven.jython.compile.plugin.test import FibSequenceCalc

import nose

def main():
	fc = FibSequenceCalc()
	print dir(nose)
	print [fc.calc() for i in dir(nose)]

