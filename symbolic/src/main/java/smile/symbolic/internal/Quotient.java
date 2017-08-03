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
public class Quotient extends BinaryNode {

   public Quotient(Expression left, Expression right) {

       super(left, right, BinaryOperator.DIVIDE);
    }

    @Override
    public Expression derive() {
        return new Quotient(
                new Difference(
                        new Product(left.derive(), right),
                        new Product(left, right.derive())
                ),
                new Product(right, right)
        );
    }

    @Override
    public Expression reduce() {

       Expression n = left.reduce();
       Expression d = right.reduce();

       if(n.getType().equals("0") || d.getType().equals("1")) {
           return n;
       }
       if(n.equals(d)) {
           return new Constant(1);
       }
        if (d instanceof Quotient) {
            if (n instanceof Quotient) {
                return new Quotient(
                        new Product(n.getLeftChild(), d.getRightChild()),
                        new Product(d.getLeftChild(), n.getRightChild())
                ).reduce();
            } else {
                return new Quotient(
                        new Product(n, d.getRightChild()),
                        d.getRightChild()
                ).reduce();
            }
        } else if (n instanceof Quotient) {
            return new Quotient(
                    n.getLeftChild(),
                    new Product(d, n.getRightChild())
            ).reduce();
        }

        return new Quotient(n, d);
    }

    @Override
    public double getValue() {
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Expression)) {
            return false;
        }

        if (object == this) {
            return true;
        }

        Expression t = reduce();
        Expression o = ((Expression) object).reduce();

        if (!(t instanceof Quotient)) {
            return t.equals(o);
        }

        if (!(o instanceof Quotient)) {
            return false;
        }

        Quotient qt = (Quotient) t;
        Quotient qo = (Quotient) o;

        return qt.getLeftChild().equals(qo.getLeftChild())
                && qt.getRightChild().equals(qo.getRightChild());
    }
}
