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

import smile.symbolic.internal.*;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Stack;
import java.util.LinkedList;
import java.util.Iterator;

/**
 *  This class represents a binary tree that represents an expression
 *     such that each node itself is an Expression and contains either
 *     one or two children, depending on if that Expression is a
 *     UnaryOperation or a BinaryOperation, respectively. </p>
 *
 * @author Ernest DeFoy
 * @see <a href="https://en.wikipedia.org/wiki/Binary_expression_tree">
 *      Wikipedia: Binary expression tree</a>
 */
public class ExpressionTree {

    private Expression root;
    private String var;
    private double val;
    private ArrayList<String> tokens;

    public ExpressionTree() {

        init("x", new ArrayList<>());
    }

   public ExpressionTree(String var, ArrayList<String> tokens) {

        init(var, tokens);
    }

    public Expression getRoot() {

        return root;
    }

    public double getVal() {

        return val;
    }

    public ArrayList<String> getTokens() {
        return tokens;
    }

    public void printTree() {

        Queue<Expression> currentLevel = new LinkedList<Expression>();
        Queue<Expression> nextLevel = new LinkedList<Expression>();

        currentLevel.add(root);

        while (!currentLevel.isEmpty()) {
            Iterator<Expression> iter = currentLevel.iterator();
            while (iter.hasNext()) {
                Expression currentNode = iter.next();
                if (currentNode.getLeftChild() != null) {
                    nextLevel.add(currentNode.getLeftChild());
                }
                if (currentNode.getRightChild() != null) {
                    nextLevel.add(currentNode.getRightChild());
                }
                System.out.print(currentNode.getType() + " ");
            }
            System.out.println();
            currentLevel = nextLevel;
            nextLevel = new LinkedList<Expression>();

        }
        System.out.println("==================");
    }

    public void init(String var, ArrayList<String> tokens) {
        this.var = var;
        this.val = 0;
        this.tokens = tokens;
        root = constructTree(tokens);
    }

    public void reduce() {
        root = root.reduce();
    }

    public void derive() {
        root = root.derive();
    }

    public void derive(double val) {

        derive();

        // val = root.evalulate(val);
    }

    public String toString() {
        return createInfix(root);
    }

    //creates string representing infix expression
    public String createInfix(Expression root)
    {
        String str = "";
        String closeParen = "";
        String leftOpenParen = "";
        String leftCloseParen = "";

        if(root == null) {
            return str;
        }

        if (ExpressionParser.isOperand(root.getType(), var)) {
            str += root.getType();

        }
        else if(root.getType().equals("$")) {
            str += "-";
        }
        else if(ExpressionParser.isFunction(root.getType())) { // does not include unary minus
            str += root.getType();
            str += "(";
            closeParen = ")";
        }
        else { // is operator
            int parentPrecedence = ExpressionParser.getPrecedence(root.getType());

            str += root.getType();
            if(ExpressionParser.isOperator(root.getLeftChild().getType()) && (ExpressionParser.getPrecedence(root.getLeftChild().getType()) < parentPrecedence)) {
                leftOpenParen = "(";
                leftCloseParen = ")";
            }
			else if(ExpressionParser.isOperator(root.getRightChild().getType()) && (ExpressionParser.getPrecedence(root.getRightChild().getType()) < parentPrecedence)) {
					str += "(";
					closeParen = ")";

            }
        }

        return leftOpenParen + createInfix(root.getLeftChild()) + leftCloseParen + str + createInfix(root.getRightChild()) + closeParen;
    }

    // reads the "tokens" in order from the list and builds a tree
    public Expression constructTree(ArrayList<String> postTokens)
    {
        Expression root = null;
        Stack<Expression> nodes = new Stack<>();

        for(String str: postTokens)
        {
            if(str.isEmpty()){
                continue;
            }
            if(str.matches("[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?")) {
                nodes.push(new Constant(Double.parseDouble(str)));
            }
            else if(str.equals(var)) {
                nodes.push(new Variable(var));
            }
            else if(!nodes.isEmpty() && ExpressionParser.isFunction(str)) {
                Expression function = matchFunc(str, nodes.pop());
                nodes.push(function);
            }
            else if(!nodes.isEmpty() && str.equals("$")) {
                Expression unaryMinus = new Negation(nodes.pop());
                nodes.push(unaryMinus);
            }
            else if(!nodes.isEmpty()){
                Expression right = nodes.pop();
                Expression binaryOperator = matchOperator(str, nodes.pop(), right);
                nodes.push(binaryOperator);
            }
        }

        if(!nodes.isEmpty())
            root = nodes.pop();

        return root;
    }

    public Expression matchFunc(String str, Expression exp) {

        switch(str) {
            case "ln":
                return new NaturalLogarithm(exp);
            case "log":
                return new Logarithm(exp);
            case "sin":
                return new Sine(exp);
            case "cos":
                return new Cosine(exp);
            case "tan":
                return new Tangent(exp);
            case "csc":
                return new Cosecant(exp);
            case "sec":
                return new Secant(exp);
            case "cot":
                return new Cotangent(exp);
            default: System.out.println("BINARYEXPRESSIONTREE: Ambiguous function: " + str + " " + str.isEmpty());
                return null;
        }
    }

    public Expression matchOperator(String str, Expression left, Expression right) {

        switch(str) {
            case "+":
                return new Sum(left, right);
            case "-":
                return new Difference(left, right);
            case "*":
                return new Product(left, right);
            case "/":
                return new Quotient(left, right);
            case "^":
                return new Exponent(left, right);
            default: System.out.println("BINARYEXPRESSIONTREE: Ambiguous operator: " + str + " " + str.isEmpty());
                return null;
        }
    }
}
