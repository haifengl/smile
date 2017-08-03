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

package smile.symbolic;

import java.util.ArrayList;
import java.util.Stack;
import java.util.Arrays;

/**
 * @author Ernest DeFoy
 */
public class ExpressionParser {

    private String expression;
    private String var;
    private ArrayList<String> tokens;

    public String getExpression() {

        return expression;
    }

    public String getVar() {

        return var;
    }

    public ArrayList<String> getTokens() {

        return tokens;
    }

    public static boolean isOperator(String str)
    {
        if(str.matches("[+*/^-]"))
            return true;

        return false;
    }

    public static boolean isOperand(String str, String var)
    {
        if(str.matches("[0-9]+") || str.equals(var))
            return true;

        return false;
    }

    public static boolean isFunction(String str)
    {
        String[] funcs = {"sin", "cos", "tan", "csc", "sec", "cot", "log", "ln"};

        return Arrays.asList(funcs).contains(str);
    }

    public static int getPrecedence(String str)
    {
        if(isFunction(str))
            return 5;

        int val = 0;

        if(str.equals("+"))
            val = 2;
        else if(str.equals("-"))
            val = 2;
        else if(str.equals("*") || str.equals(("/")))
            val = 3;
        else if(str.equals("^") || str.equals("$"))
            val = 4;

        return val;
    }

    public static boolean isLeftAssociative(String s)
    {
        if(s.equals("^") || s.equals("$") || s.equals("+") || s.equals("*"))
            return false;

        return true;
    }

    // modifies the string to look more like it would if someone wrote the expression
    // out on paper
    public String format(String s)
    {
        for(int i = 0; i < s.length(); i++) {
            if(s.substring(i, i + 1).equals("*") && i > 0)
                if(isOperand(s.substring(i - 1, i), var) && i < s.length() - 1 && s.substring(i + 1, i + 2).equals(var))
                    s = s.substring(0, i) + s.substring(i + 1);
        }

        return s;
    }

    public String parse(String expression) throws InvalidExpressionException {

        this.expression = expression;

        try {
            var = check();
        } catch (InvalidExpressionException e) {
            e.printStackTrace();
        }

        this.expression = formatString(expression);
        System.out.println(this.expression);
        tokens = tokenize(this.expression);

        return this.expression;
    }

    private String check() throws InvalidExpressionException {

        expression = expression.replaceAll("\\s","");
        expression = expression.toLowerCase();
        String var = "";

        if(expression.length() == 0) {
            throw new InvalidExpressionException("Empty Expression");
        }

        if(!expression.matches("[a-zA-Z0-9+*/^()-]+")) { // contains only operators, numbers, or letters
            throw new InvalidExpressionException("Syntax Error");
        }

        if(expression.matches("[+*/^()-]+")) { // doesn't contain any operands
            throw new InvalidExpressionException("Syntax Error");
        }

        String firstChar = expression.substring(0, 1);
        String lastChar = expression.substring(expression.length() - 1, expression.length());

        if(!firstChar.equals("-") && isOperator(firstChar) || firstChar.equals(")") || isOperator(lastChar) || lastChar.equals("(")) {
            throw new InvalidExpressionException("Syntax Error");
        }


        for(int i = 0; i < expression.length(); i++) {

            String temp = "";

            while(i < expression.length() && expression.substring(i, i + 1).matches("[a-zA-Z]")) {
                temp += expression.substring(i, i + 1);
                i++;
            }

            if(temp.length() == 1) {
                //i--; // ?? i must be decremented from the above while loop in this if block so the program can check the last character in the string
                if(var.length() == 0)
                    var = temp;
                if(!temp.equals(var)) {
                    throw new InvalidExpressionException("For now, expression must contain a single variable");
                }
                else if(i < expression.length() && expression.substring(i, i + 1).matches("[0-9]+")) {
                    throw new InvalidExpressionException("Syntax Error");
                }
            }
            else if(isFunction(temp)) {

                if(i < expression.length()) {
                    if(!expression.substring(i, i + 1).equals("(")) {
                        //System.out.println("Syntax Error: " + temp + " needs a parenthesis after it");// no parenthesis after function (EX: sin5)
                        throw new InvalidExpressionException("Syntax Error");
                    }
                }
                else {
                    //System.out.println("Syntax Error: " + temp + " needs an input"); // nothing after function (EX: 5 + sin)
                    throw new InvalidExpressionException("Syntax Error");
                }
            }
            else if(temp.length() != 0){
                //System.out.println(temp + ": function not found");
                throw new InvalidExpressionException(temp + ": function not found");
            }

            //i--; // ?? i must be decremented since it was left incremented in the above while loop
        }


        int cntOpenParen = 0;
        int cntCloseParen = 0;

        for(int i = 0; i < expression.length() - 1; i++) {

            String tmp1 = expression.substring(i, i + 1);
            String tmp2 = expression.substring(i + 1, i + 2);

            if(tmp1.equals("-")) {
                if(isOperator(tmp2) || tmp2.equals(")")) {
                    //System.out.println("Syntax Error: " + tmp1 + tmp2);
                    throw new InvalidExpressionException("Syntax Error");
                }
            }
            else if(tmp2.equals("-")) { // Also prevents next else if from rejecting an operator followed by a unary minus
                if(tmp1.equals("(")) {
                    //System.out.println("Syntax Error: " + tmp1 + tmp2);
                    throw new InvalidExpressionException("Syntax Error");
                }
            }
            else if((isOperator(tmp1) || tmp1.equals("(")) && (isOperator(tmp2) || tmp2.equals(")"))) {
                //System.out.println("Syntax Error: " + tmp1 + tmp2); // two operands in a row (examples: ++, (+, ())
                throw new InvalidExpressionException("Syntax Error");
            }
            else if(expression.substring(i, i + 1).equals("("))
                cntOpenParen++;
            else if(expression.substring(i, i + 1).equals(")"))
                cntCloseParen++;
        }

        if(cntOpenParen < cntCloseParen) { // found a ")" when the end of the expression was expected
            //System.out.println("Syntax Error: found ')' but expected end of expression");
            throw new InvalidExpressionException("Syntax Error");
        }

        return var;
    }

    // adds and deletes characters to aid in the creation of the binary expression tree
    private String formatString(String exp)
    {
        exp = exp.replaceAll("\\s",""); // why
        exp = exp.toLowerCase();

        int count = 0;

        if(exp.substring(0, 1).equals("-")) { // if expression starts with a minus sign, it is a unary one
            exp = "$" + exp.substring(1); // replace
        }

        for(int i = 0; i < exp.length(); i++) {
            if(exp.substring(i,  i + 1).equals("("))
                count++;
            else if(exp.substring(i, i + 1).equals(")"))
                count--;
        }
        while(count > 0) {
            exp += ")";
            count--;
        }

        // At the operators, when the operator is "-" and it is preceded by another operator,
        // or preceded by a left parenthesis, or when it is the first character of the input
        // it is a unary minus rather than binary. In this case, I change it to another
        // character, '$', and make its precedence the same as that of '^'.
        for(int i = 0; i < exp.length() - 1; i++) {
            String tmp1 = exp.substring(i, i + 1);
            String tmp2 = exp.substring(i + 1, i + 2);
            if(tmp2.equals("-") && (ExpressionParser.isOperator(tmp1) || tmp1.equals("(")))
                exp = exp.substring(0, i + 1) + "$" + exp.substring(i + 2);
            else if((tmp1.matches("[0-9]+") || tmp1.equals(var)) && (tmp2.equals("(") || tmp2.equals(var)))
                exp = exp.substring(0, i + 1) + "*" + exp.substring(i + 1);
        }

        return exp;
    }

    // separates the expression string into "tokens" and sorts them in
    // postfix order
    public ArrayList<String> tokenize(String exp)
    {
        ArrayList<String> tokens = new ArrayList<>();
        Stack<String> stack = new Stack<>();

        for(int i = 0; i < exp.length(); i++)
        {
            String token = "";

            if(isOperator(exp.substring(i, i + 1)) || exp.substring(i, i + 1).equals("$")) {
                token = exp.substring(i, i + 1);
                while ((!stack.isEmpty() && (isOperator(stack.peek()) || stack.peek().equals("$")))
                        && ((isLeftAssociative(token) && getPrecedence(token) <= getPrecedence(stack.peek()))
                        || (!isLeftAssociative(token) && getPrecedence(token) < getPrecedence(stack.peek())))) {

                    tokens.add(stack.pop());
                }
                stack.push(token);
            }
            else if(exp.substring(i, i + 1).equals(var)) {
                token = var;
                tokens.add(token);
            }
            else if(exp.substring(i, i + 1).equals("(")) {
                token = exp.substring(i, i + 1);
                stack.push(token);
            }
            else if(exp.substring(i, i + 1).equals(")")) {
                while(!stack.isEmpty() && !stack.peek().equals("(")) {
                    tokens.add(stack.pop());
                }

                if(!stack.isEmpty())
                    stack.pop();
                if(!stack.isEmpty() && isFunction(stack.peek())) {
                    tokens.add(stack.pop());
                }
            }
            else if(exp.substring(i, i + 1).matches("[0-9]+")) {
                while(i < exp.length() && exp.substring(i, i + 1).matches("[0-9]+")) {
                    token += exp.substring(i, i + 1);
                    i++;
                }
                tokens.add(token);
                i--; // i was left incremented after the while loop
            }
            else if(exp.substring(i, i + 1).equals(var)) {
                tokens.add(token);
            }
            else {
                while(i < exp.length() && exp.substring(i, i + 1).matches("[a-zA-Z]+")) {
                    token += exp.substring(i, i + 1);
                    i++;
                }

                if(token.length() != 0) {
                    stack.push(token);
                }
            }
        }

        while(!stack.isEmpty()) {
            tokens.add(stack.pop());
        }
        System.out.println(tokens);
        return tokens;
    }
}
