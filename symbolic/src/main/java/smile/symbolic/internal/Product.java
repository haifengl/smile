/*******************************************************************************
 * Copyright (c) 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Product extends BinaryNode {

    public Product(Expression left, Expression right) {

        super(left, right, BinaryOperator.MULTIPLY);
    }

    @Override
    public Expression derive() {

        Expression l = left.derive();
        Expression r = right.derive();



        return new Sum(
                new Product(l, right),
                new Product(left, r)
        );
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        // C*C ==> C
        // x*C ==> C*x
        if(l instanceof Constant || r instanceof Constant) {
            if(l.getType().equals("1")) { // Ex: 1*5 (polish: 15*) ==> 5
                return r;
            }
            if(r.getType().equals("1")) { // Ex: 5*1 (polish: 51*) ==> 5
                return l;
            }
            if(l.getType().equals("0")) { // Ex: 0*5 (polish: 05*) ==> 0
                return l;
            }
            if(r.getType().equals("0")) { // Ex: 5*0 (polish: 50*) ==> 0
                return r;
            }
            if(l instanceof Constant && r instanceof Constant) { // Ex: 5*5 (polish: 55*) ==> 25
                return new Constant(l.getValue() * r.getValue());
            }
            if(!(l instanceof Constant)) { // Ex: x*5 (polish: x5*) ==> 5*x
                Expression tmp = l;
                l = r;
                r = tmp;
            }
        }

        // Ex: x^2 * C ==> C * x^2
        if(l instanceof Exponent) {
            Expression tmp = l;
            l = r;
            r = tmp;
        }

        // C*x*C*x ==> (C*C)*(x*x)
        if(r instanceof Product && l instanceof Product) {

        }

        // C*x*C ==> (C*C)*x
        if(r instanceof Product) {
            if (l instanceof Constant) {
                if (r.getLeftChild() instanceof Constant) { // Ex: 4*4*x (polish: 44x**) ==> 16*x
                    return new Product(
                            new Constant(l.getValue() * r.getLeftChild().getValue()),
                            r.getRightChild()
                    );
                }
                if (r.getRightChild() instanceof Constant) { // Ex: 4*x*4 (polish: 4x4**) ==> 16*x
                    return new Product(
                            new Constant(l.getValue() * r.getRightChild().getValue()),
                            r.getLeftChild()
                    );
                }
            }
        }

        // C*x*C => (C*C)*x
        if(l instanceof Product) {
            if (r instanceof Constant) {
                if (l.getLeftChild() instanceof Constant) { // Ex: 4*x*4 (polish: 4x*4*) ==> 16*x
                    return new Product(
                            new Constant(r.getValue() * l.getLeftChild().getValue()),
                            l.getRightChild()
                    );
                }
                if (l.getRightChild() instanceof Constant) { // Ex: x*4*4 (polish: x4*4*) ==> 16*x
                    return new Product(
                            new Constant(r.getValue() * l.getRightChild().getValue()),
                            l.getLeftChild()
                    );
                }
            }
        }

        if(r instanceof Quotient) {
            Expression numerator;
            if(r.getLeftChild().getType().equals("1")) {
                numerator = left;
            }
            else {
                numerator = new Product(
                        left,
                        r.getLeftChild()
                );
            }

            return new Quotient(
                    numerator,
                    r.getRightChild()
            );
        }

        return new Product(l, r);
    }

    @Override
    public double getValue() {

        if(left instanceof Constant)
            return left.getValue();
        if(right instanceof Constant)
            return right.getValue();
        return 0;
    }
}
