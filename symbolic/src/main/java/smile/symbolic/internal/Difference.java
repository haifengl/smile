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
public class Difference extends BinaryNode {

    public Difference(Expression left, Expression right) {

        super(left, right, BinaryOperator.MINUS);
    }

    @Override
    public Expression derive() {

        return new Difference(left.derive(), right.derive());
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        if(l instanceof Constant || r instanceof Constant) {

            if(l.getValue() == 0) { // Example: 0 - sin(x) => -sin(x)
                return new Negation(r);
            }
            if(r.getValue() == 0) { // Example: sin(x) - 0 => sin(x)
                return l;
            }

            if(l instanceof Constant && r instanceof Constant) {
                return new Constant(l.getValue() - r.getValue());
            }
        }

        if(l instanceof Negation) {
            return new Difference(r, l.getRightChild());
        }
        if(r instanceof Negation) {
            return new Sum(l, r.getRightChild());
        }

        return new Difference(l, r);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
