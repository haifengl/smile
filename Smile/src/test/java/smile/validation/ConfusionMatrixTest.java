package smile.validation;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConfusionMatrixTest {

	@Test
	public void test() {
		
		int[] truth = 		{0,1,2,3,4,5,0,1,2,3,4,5};
		int[] prediction = 	{0,1,2,4,5,2,1,2,4,5,4,1};
		
		ConfusionMatrix cmGen = new ConfusionMatrix();
		
		int[][] matrix = cmGen.generate(truth, prediction);
		
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
