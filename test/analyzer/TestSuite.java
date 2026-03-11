package analyzer;

import analyzer.tests.IntermediateCodeGenFallTest;
import analyzer.tests.IntermediateCodeGenTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Created: 17-08-09
 * Last Changed: 17-08-09
 * Author: Nicolas Cloutier
 * <p>
 * Description: This is the class holding all the different tests.
 * If you want to add a test, simply add it in this file
 */

@RunWith(Suite.class)

@Suite.SuiteClasses({
        IntermediateCodeGenTest.class,
        IntermediateCodeGenFallTest.class
})

public class TestSuite {
}
