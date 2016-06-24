/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
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

package smile.demo.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple utility to parse command line parameters.
 *
 * @author Haifeng Li
 */
public class ParameterParser {

    /**
     * Supported parameter types.
     */
    public enum ParameterType {
        /**
         * Option without parameter.
         */
        MONADIC,
        /**
         * Integer parameter.
         */
        INTEGER,
        /**
         * Real-valued parameter.
         */
        FLOAT,
        /**
         * String parameter.
         */
        STRING
    };

    private Map<String, Parameter> parameters;
    private String usage;

    private class Parameter {
        String name;
        String value;
        ParameterType paramType;
        boolean mandatory;

        Parameter(String name, ParameterType paraType) {
            this(name, paraType, false);
        }

        Parameter(String name, ParameterType type, boolean mandatory) {
            this.name = name;
            this.paramType = type;
            this.mandatory = mandatory;
        }

        Parameter(String name, ParameterType paraType, String defaultValue) {
            this(name, paraType, false);
            this.value = defaultValue;
        }
    }

    /**
     * Constructor.
     * @param usage the usage information that will be display if -h, -help
     * or invalid parameter presents.
     */
    public ParameterParser(String usage) {
        this.usage = usage;
        parameters = new HashMap<>();
    }

    /**
     * Add a (optional) parameter.
     * @param name the name of parameter.
     * @param type the type of parameter.
     */
    public void addParameter(String name, ParameterType type) {
        parameters.put(name, new Parameter(name, type));
    }

    /**
     * Add a parameter.
     * @param name the name of parameter.
     * @param type the type of parameter.
     * @param mandatory true if the parameter is mandatory.
     */
    public void addParameter(String name, ParameterType type, boolean mandatory) {
        parameters.put(name, new Parameter(name, type, mandatory));
    }

    /**
     * Add a (optional) parameter.
     * @param name the name of parameter.
     * @param type the type of parameter.
     * @param defaultValue the default value of parameter.
     */
    public void addParameter(String name, ParameterType type, String defaultValue) {
        parameters.put(name, new Parameter(name, type, defaultValue));
    }

    /**
     * Returns the value of a given parameter. Null if the parameter
     * does not present.
     */
    public String get(String name) {
        Parameter param = parameters.get(name);
        if (param != null)
            return param.value;
        else
            return null;
    }

    /**
     * Parses the args array looking for monads, arguments without value
     * such as "-verbose" and if found inserts, forces, a value of "1" and
     * returns the transformed args as a List
     *
     * @param args  array of input values
     * @return a list of expanded name-value pair if monads are detected
     */
    private List<String> filterMonadics(String[] args) {// name-value for monads
        List<String> filteredArgs = new ArrayList<>();       // Y <- return List
        for (String arg : args) {                        // iterate over args
            filteredArgs.add(arg);
            Parameter param = parameters.get(arg);
            if (param != null && param.paramType == ParameterType.MONADIC) {
                filteredArgs.add("1");                             // insert a value to "1"
            }
        }
        return filteredArgs;                                       // return List of args
    }

    /**
     * Parse the arguments.
     * @param args arguments
     * @return a list of strings which are not parameters, e.g. a list of file
     * name.
     */
    public List<String> parse(String[] args) {    // merge args & defaults
        List<String> extras = new ArrayList<>();
        List<String> filteredArgs = filterMonadics(args);          // detect and fill mons
        for (int i = 0; i < filteredArgs.size(); i++) {
            String key = filteredArgs.get(i);
            if (key.equalsIgnoreCase("-h") || key.equalsIgnoreCase("-help")) {
                System.out.println(usage);
                System.exit(0);
            }
            
            if (parameters.containsKey(key)) {
                Parameter param = parameters.get(key);
                param.value = filteredArgs.get(i+1);
                switch(param.paramType) {
                    case INTEGER:
                        try {
                            Integer.parseInt(param.value);
                        } catch (Exception ex) {
                            System.err.println("Invalid parameter " + param.name + ' ' + param.value);
                            System.err.println(usage);
                            System.exit(1);
                        }
                        break;

                    case FLOAT:
                        try {
                            Double.parseDouble(param.value);
                        } catch (Exception ex) {
                            System.err.println("Invalid parameter " + param.name + ' ' + param.value);
                            System.err.println(usage);
                            System.exit(1);
                        }
                        break;
                        
                    default:
                    	// Just to remove unmatched case warning
                }
                i++;
            } else {
                extras.add(key);
            }
        }

        for(String key : parameters.keySet()) {
            Parameter param = parameters.get(key);
            if (param.mandatory && param.value == null) {
                System.err.println("Missing mandatory parameter: " + key);
                System.err.println(usage);
                System.exit(1);
            }
        }

        return extras;   // parsed + defaults
    }
}

