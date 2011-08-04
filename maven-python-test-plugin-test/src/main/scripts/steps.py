from freshen import *

from fib import fib

@When('I calculate the first (\d+) fibonacci numbers')
def calculate(n):
	n = int(n)
	ftc.fibval = [1,2,3,4]
	ftc.fibval = [fib(i) for i in range(1, n+1)] 

@Then('it should give me (\[.*\])')
def check_result(value):
	assert_equal(str(ftc.fibval), value)

