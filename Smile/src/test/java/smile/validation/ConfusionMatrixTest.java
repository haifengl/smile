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
package smile.validation;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConfusionMatrixTest {

	@Test
	public void test() {
		
		int[] truth = 		{0,1,2,3,4,5,0,1,2,3,4,5};
		int[] prediction = 	{0,1,2,4,5,2,1,2,4,5,4,1};
		
		ConfusionMatrix cmGen = new ConfusionMatrix(truth, prediction);
		
		int[][] matrix = cmGen.getMatrix();
		
		System.out.println(cmGen.toString());
		
		int[] expected = {1,1,1,0,1,0};
		
		for(int i = 0; i < expected.length; i++){
			//main diagonal test
			assertEquals(matrix[i][i], expected[i]);
			//class 3 not predicted test
			assertEquals(matrix[i][3], 0);
		}
	}

}
