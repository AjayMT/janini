import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

/**
 * Test the WebServer class.
 */
class TestSnippet {
    @BeforeClass
    fun initialize() {
        Source.initialize(null);
    }

    /**
     * Test simple script.
     */
    @Test
    fun testMath() {
        val snippet = Snippet().run("""
int a = 3;
int b = 4;
System.out.print(a + b);
""")
        Assert.assertEquals(snippet.output, "7")
        Assert.assertTrue(snippet.executed)
        Assert.assertTrue(snippet.checkstyleSucceeded)
        Assert.assertFalse(snippet.timedOut)
    }

    @Test
    fun testExit() {
        val snippet = Snippet().run("""
System.exit(-1);
""")
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.crashed)
        Assert.assertFalse(snippet.timedOut)
    }

    @Test
    fun testNullDeference() {
        val snippet = Snippet().run("""
Object reference = null;
System.out.println(reference.toString());
""")
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.crashed)
        Assert.assertFalse(snippet.timedOut)
    }

    @Test
    fun testTimeout() {
        val snippet = Snippet().run("""
int i = 0;
while (true) {
    i++;
}
""")
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.timedOut)
        Assert.assertFalse(snippet.crashed)
        Assert.assertTrue(snippet.timeoutLength / 1000.0 <= snippet.executionLength)
        Assert.assertTrue(snippet.executionLength <= (snippet.timeoutLength / 1000.0 * 1.2))
    }

    @Test
    fun testPrivateVisibility() {
        val snippet = Snippet().run("""
class Test {
    private int value;
}
Test t = new Test();
t.value = 5;
System.out.print(t.value);
""")
        Assert.assertEquals(snippet.output, "5")
        Assert.assertTrue(snippet.executed)
        Assert.assertFalse(snippet.timedOut)
        Assert.assertFalse(snippet.crashed)
    }

    @Test
    fun testPrivateFunction() {
        val snippet = Snippet().run("""
static int test() {
    return 5;
}
System.out.print(test());
""")
        Assert.assertEquals(snippet.output, "5")
        Assert.assertTrue(snippet.executed)
        Assert.assertFalse(snippet.timedOut)
        Assert.assertFalse(snippet.crashed)
    }

    @Test
    fun testGenericsAreBrokenUsingJanino() {
        val snippet = Snippet("Janino")
        snippet.run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String broken = list.get(0);
System.out.println(broken);
        """)
        Assert.assertFalse(snippet.compiled)
        Assert.assertFalse(snippet.compiled)
    }

    @Test
    fun testGenericsWorkUsingJDK() {
        val snippet = Snippet("JDK")
        snippet.run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String working = list.get(0);
System.out.print(working);
        """)
        Assert.assertEquals(snippet.output, "Geoffrey")
        Assert.assertEquals(snippet.compiler, "JDK")
        Assert.assertTrue(snippet.executed)
        Assert.assertFalse(snippet.timedOut)
        Assert.assertFalse(snippet.crashed)
    }

    @Test
    fun testGenericsTriggerTheJDK() {
        val snippet = Snippet()
        snippet.run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String working = list.get(0);
System.out.print(working);
        """)
        Assert.assertEquals(snippet.output, "Geoffrey")
        Assert.assertEquals(snippet.compiler, "JDK")
        Assert.assertTrue(snippet.executed)
        Assert.assertFalse(snippet.timedOut)
        Assert.assertFalse(snippet.crashed)
    }

    @Test
    fun testGenericsWorkWithCasts() {
        val snippet = Snippet().run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String working = (String) list.get(0);
System.out.print(working);
        """)
        Assert.assertTrue(snippet.executed)
        Assert.assertEquals(snippet.output, "Geoffrey")
        Assert.assertTrue(snippet.executed)
        Assert.assertFalse(snippet.timedOut)
        Assert.assertFalse(snippet.crashed)
    }

    @Test
    fun testCheckstyleIndentation() {
        val snippet = Snippet().run("""
import java.util.ArrayList;
static void test() {
  int me = 5;
}
test();
        """)
        Assert.assertFalse(snippet.checkstyleSucceeded)
        // Assert.assertFalse(snippet.compiled)
        // Assert.assertFalse(snippet.executed)
    }

    @Test
    fun testCheckstyleBraces() {
        val snippet = Snippet().run("""
import java.util.ArrayList;
if (0 < 1)
{
    System.out.println("Big");
}""")
        Assert.assertFalse(snippet.checkstyleSucceeded)
        // Assert.assertFalse(snippet.compiled)
        // Assert.assertFalse(snippet.executed)
    }

    @Test
    fun testCheckstyleWhitespace() {
        val snippet = Snippet().run("""
import java.util.ArrayList;
for(int=0;i<10;i++){
    System.out.println("Big");
}""")
        Assert.assertFalse(snippet.checkstyleSucceeded)
        // Assert.assertFalse(snippet.compiled)
        // Assert.assertFalse(snippet.executed)
    }
}
