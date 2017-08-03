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
public abstract class Expression {
    public abstract Expression derive();
    public abstract Expression reduce();
    public abstract String getType();
    public abstract double getValue();
    public abstract Expression getLeftChild();
    public abstract Expression getRightChild();

    enum BinaryOperator {

        PLUS("+"),
                MINUS("-"),
                MULTIPLY("*"),
                DIVIDE("/"),
                POWER("^");

        final private String display;

        BinaryOperator(String display) {
            this.display = display;
        }

        public String toString() {
            return display;
        }

    }

    enum UnaryOperator {

        NEGATE("$"),
        NATURALLOG("ln"),
        LOG("log"),
        SIN("sin"),
        COS("cos"),
        TAN("tan"),
        CSC("csc"),
        SEC("sec"),
        COT("cot");

        final private String display;

        UnaryOperator(String display) {
            this.display = display;
        }

        public String toString() {
            return display;
        }

    }
}



