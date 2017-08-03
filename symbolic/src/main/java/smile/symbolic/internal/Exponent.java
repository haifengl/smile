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
public class Exponent extends BinaryNode {

    public Exponent(Expression left, Expression right) {

        super(left, right, BinaryOperator.POWER);
    }

    @Override
    public Expression derive() {

        if(left instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                right,
                new Product(
                        left.derive(),
                        new Exponent(
                                left,
                                new Difference(
                                        right,
                                        new Constant(1)
                                )
                        )
                )
        );
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        if(r instanceof Constant) {
            if(r.getValue() == 0)
                return new Constant(1);
            if(r.getValue() == 1)
                return left;
        }

        return new Exponent(l, r);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
