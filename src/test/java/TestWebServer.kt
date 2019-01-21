import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonArray
import org.testng.Assert
import org.testng.annotations.Test
import com.eclipsesource.json.JsonObject
import org.testng.annotations.BeforeClass

/**
 * Test the WebServer class.
 */
class TestWebServer {
    @BeforeClass
    fun initialize() {
        Source.initialize(null);
    }

    private fun isOK(testObject : JsonObject) {
        Assert.assertTrue(testObject.get("executed").asBoolean())
    }

    /**
     * Test crash script.
     */
    @Test
    fun testCrashScript() {
        val submission = JsonObject()
        submission.add("source", "System.exit(-1);")
        Assert.assertNotNull(Json.parse(WebServer.run(submission.toString())).asObject())
        // If this fails it shuts down the test runner
    }

    /**
     * Test simple class.
     */
    @Test
    fun testSimpleClass() {
        val submission = JsonObject()
        submission.add("as", "SimpleCompiler")
        submission.add("class", "Question")
        val source = """
public class Question {
    public static void main(String[] unused) {
        int a = 3;
        int b = 4;
        System.out.println(a + b);
    }
}""".trim()
        submission.add("sources", JsonArray().add(source))
        val result = Json.parse(WebServer.run(submission.toString())).asObject()
        Assert.assertEquals(Integer.parseInt(result.get("output").asString().trim()), 7)
    }

    /**
     * Test crash class.
     */
    @Test
    fun testCrashClass() {
        val submission = JsonObject()
        submission.add("as", "SimpleCompiler")
        submission.add("class", "Question")
        val source = """
public class Question {
    public static void main(String[] unused) {
        System.exit(-1);
    }
}""".trim()
        submission.add("sources", JsonArray().add(source))
        Assert.assertNotNull(Json.parse(WebServer.run(submission.toString())).asObject())
        // If this fails it shuts down the test runner
    }

    /**
     * Test timeout class.
     */
    @Test
    fun testTimeoutClass() {
        val submission = JsonObject()
        submission.add("as", "SimpleCompiler")
        submission.add("class", "Question")
        val source = """
public class Question {
    public static void main(String[] unused) {
        while (true) { }
    }
}
""".trim()
        submission.add("sources", JsonArray().add(source))
        submission.add("requireCheckstyle", false)
        val result = Json.parse(WebServer.run(submission.toString())).asObject()
        Assert.assertTrue(result.get("timedOut").asBoolean())
    }

    /**
     * Test parallel execution.
     */
    @Test
    fun testParallelExecution() {

        listOf(IntArray(32) { it + 1 }).parallelStream()
                .forEach{ i ->
                    val source = """
public class Question {
    public static void main(String[] unused) {
        for (int i = 0; i < 1024; i++) {
            System.out.println("$i");
            for (int j = 0; j < 10000; j++) { }
        }
    }
}"""
                    val testObject = JsonObject()
                    testObject.add("as", "SimpleCompiler")
                    testObject.add("class", "Question")
                    testObject.add("timeoutLength", 1000)
                    testObject.add("requireCheckstyle", false)
                    testObject.add("sources", JsonArray().add(source))
                    val result = Json.parse(WebServer.run(testObject.toString())).asObject()
                    isOK(result)
                    Assert.assertEquals(result.get("output").asString().trim().split("\n").size, 1024)
                }

    }
}
