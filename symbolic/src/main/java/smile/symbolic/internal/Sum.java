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
public class Sum extends BinaryNode {

    public Sum(Expression left, Expression right) {

        super(left, right, BinaryOperator.PLUS);
    }

    @Override
    public Expression derive() {
        return new Sum(left.derive(), right.derive());
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        if(l instanceof Constant || r instanceof Constant) {
            if(l.getType().equals("0")) {
                return r;
            }
            if(r.getType().equals("0")) {
                return l;
            }
        }

        return new Sum(l, r);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
