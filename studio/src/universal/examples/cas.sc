// Computer Algebra System

import smile.cas._

// A computer algebra system (CAS) has the ability to manipulate
// mathematical expressions in a way similar to the traditional
// manual computations of mathematicians and scientists.

// A scalar variable x
val x = Var("x")
// The expression e is a function of x.
// For demo purpose, we include some redundant elements,
// which will be simplified away automatically.
val e = 0 * x**2 + 1 * x**1 + 1 * x**2 * cot(x**3)
println(e)

// The derivative of e with respect to x
val d = e.d(x)
println(d)

// To evaluate an expression, simply apply it on a map of values.
d("x" -> Val(1))

// We may substitute the variables with other abstract
// expression rather than only values.
val y = x + 2
println(d("x" -> y))


// Vector variables
// The dimension of vector, if not specified, is a constant value n.
val X = VectorVar("X")
val Y = VectorVar("Y")

// Constant yet abstract values
val a = Const("a")
val b = Const("b")
val f = a * X + b * Y
println(f)

println(f.d(X), f.d(Y))

// The dot product (or inner product) is a scalar.
val dot = X * Y
println(dot)

// The derivative of a scalar with respect to a vector, i.e. the gradient, is a vector.
dot.d(X)

// With operator *~, we can derive the outer product.
val outer = (2 *X) *~ (3 * Y)
println(outer)
