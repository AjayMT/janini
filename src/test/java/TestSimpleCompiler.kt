import org.codehaus.commons.compiler.CompileException
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

/**
 * Test the WebServer class.
 */
class TestSimpleCompiler {
    @BeforeClass
    fun initialize() {
        Source.initialize(null);
    }

    /**
     * Test simple math.
     */
    @Test
    fun testMath() {
        val classes = SimpleCompiler().run("""
public class Question {
    public static void main(final String[] unused) {
        int a = 3;
        int b = 4;
        System.out.print(a + b);
    }
}
""")
        Assert.assertEquals(classes.output, "7")
        Assert.assertEquals(classes.compiler, "Janino")
        Assert.assertTrue(classes.executed)
    }

    /**
     * Test attempt to exit.
     */
    @Test
    fun testExit() {
        val classes = SimpleCompiler().run("""
public class Question {
    public static void main(final String[] unused) {
        System.exit(-1);
    }
}
""")
        Assert.assertTrue(classes.compiled)
        Assert.assertEquals(classes.compiler, "Janino")
        Assert.assertFalse(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    @Test
    fun testNullDeference() {
        val classes = SimpleCompiler().run("""
public class Question {
    public static void main(final String[] unused) {
        Object reference = null;
        System.out.println(reference.toString());
    }
}
""")
        Assert.assertFalse(classes.executed)
        Assert.assertTrue(classes.crashed)
        Assert.assertFalse(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    @Test
    fun testTimeout() {
        val classes = SimpleCompiler().run("""
public class Question {
    public static void main(String[] unused) {
        int i = 0;
        while (true) {
            i++;
        }
    }
}
""")
        Assert.assertFalse(classes.executed)
        Assert.assertTrue(classes.timedOut)
        Assert.assertTrue(classes.timeoutLength / 1000.0 <= classes.executionLength)
        Assert.assertTrue(classes.executionLength <= (classes.timeoutLength / 1000.0 * 1.2))
    }

    /**
     * Test wrong class name.
     */
    @Test
    fun testWrongClassName() {
        val classes = SimpleCompiler()
        classes.run("""
public class Blah {
    public static void main(final String[] unused) {
        System.out.print("Broken");
    }
}
""")
        Assert.assertFalse(classes.compiled)
        Assert.assertFalse(classes.executed)
    }

    /**
     * Test wrong method name.
     */
    @Test
    fun testWrongMethodName() {
        val classes = SimpleCompiler()

        classes.run("""
public class Question {
    public static void broken(final String[] unused) {
        System.out.print("Broken");
    }
}
""")
        Assert.assertFalse(classes.compiled)
        Assert.assertFalse(classes.executed)
    }

    /**
     * Test wrong method signature.
     */
    @Test
    fun testWrongMethodSignature() {
        val classes = SimpleCompiler()
        classes.run("""
public class Question {
    public static void main() {
        System.out.print("Broken");
    }
}
""")
        Assert.assertFalse(classes.compiled)
        Assert.assertFalse(classes.executed)
    }

    /**
     * Verify the Janino doesn't support generics.
     */
    @Test
    fun testGenericsAreBrokenUsingJanino() {
        val classes = SimpleCompiler("Janino")

        classes.run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("Geoffrey");
        String broken = list.get(0);
        System.out.print(broken);
    }
}
""")
        Assert.assertFalse(classes.compiled)
        Assert.assertFalse(classes.executed)
    }

    /**
     * Verify that generics work using the JDK compiler.
     */
    @Test
    fun testGenericsWorkUsingJDK() {
        val classes = SimpleCompiler("JDK").run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("Geoffrey");
        String working = list.get(0);
        System.out.print(working);
    }
}
""")

        Assert.assertEquals(classes.output, "Geoffrey")
        Assert.assertEquals(classes.compiler, "JDK")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Verify that generics trigger the JDK.
     */
    @Test
    fun testGenericsTriggerTheJDK() {
        val classes = SimpleCompiler().run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("Geoffrey");
        String working = list.get(0);
        System.out.print(working);
    }
}
""")

        Assert.assertEquals(classes.output, "Geoffrey")
        Assert.assertEquals(classes.compiler, "JDK")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Verify that imports work properly.
     */
    @Test
    fun testImports() {
        val classes = SimpleCompiler()
        classes.requireCheckstyle = false;
        classes.run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        System.out.print("Worked");
    }
}
""")
        Assert.assertEquals(classes.output, "Worked")
        Assert.assertEquals(classes.compiler, "Janino")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test multiple classes in same source.
     */
    @Test
    fun testMultipleClassesInSingleSource() {
        val classes = SimpleCompiler().run("""
public class Other {
    public String toString() {
        return "Working";
    }
}
public class Question {
    public static void main(final String[] unused) {
        Other other = new Other();
        System.out.print(other.toString());
    }
}
    """)
        Assert.assertEquals(classes.output, "Working")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test multiple classes in same source in wrong order.
     */
    @Test
    fun testMultipleClassesInSingleSourceInWrongOrder() {
        val classes = SimpleCompiler().run("""
public class Question {
    public static void main(final String[] unused) {
        Other other = new Other();
        System.out.print(other.toString());
    }
}
public class Other {
    public String toString() {
        return "Working";
    }
}
    """)

        Assert.assertEquals(classes.output, "Working")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test multiple classes in multiple sources.
     */
    @Test
    fun testMultipleClassesInMultipleSources() {
        val classes = SimpleCompiler("Janino").run("""
public class Other {
    public String toString() {
        return "Working";
    }
}
""","""
public class Question {
    public static void main(final String[] unused) {
        Other other = new Other();
        System.out.print(other.toString());
    }
}
""")

        Assert.assertEquals(classes.output, "Working")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test multiple classes in multiple sources with imports.
     */
    @Test
    fun testMultipleClassesInMultipleSourcesWitImports() {
        val classes = SimpleCompiler("Janino").run("""
import java.util.HashMap;
public class Other {
    public String toString() {
        HashMap hashMap = new HashMap();
        return "Working";
    }
}
""","""
import java.util.ArrayList;
public class Question {
    public static void main(final String[] unused) {
        ArrayList arrayList = new ArrayList();
        Other other = new Other();
        System.out.print(other.toString());
    }
}
""")

        Assert.assertEquals(classes.output, "Working")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test multiple classes in multiple sources in the wrong order.
     *
     * Currently we expect this to fail.
     */
    @Test
    fun testMultipleClassesInMultipleSourcesInWrongOrder() {
        val classes = SimpleCompiler("Janino")
        classes.run("""
public class Question {
    public static void main(final String[] unused) {
        Other other = new Other();
        System.out.print(other.toString());
    }
}
""","""
    public class Other {
    public String toString() {
        return "Working";
    }
}
""")
        Assert.assertFalse(classes.compiled)
        Assert.assertFalse(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test stream operators. These require new permissions.
     */
    @Test
    fun testStreams() {
        val classes = SimpleCompiler("JDK")
        classes.run("""
import java.util.Arrays;

public class Question {
    public static void main(final String[] unused) {
        Arrays.asList("a1", "a2", "b1", "c2", "c1")
            .stream()
            .filter(s -> s.startsWith("c"))
            .map(String::toUpperCase)
            .sorted()
            .forEach(System.out::print);
    }
}
""")
        Assert.assertEquals(classes.output, "C1C2")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }

    /**
     * Test generic methods. These require new permissions.
     */
    @Test
    fun testGenericMethod() {
        val classes = SimpleCompiler("JDK")
        classes.run("""
public class A implements Comparable<A> {
    public int compareTo(A other) {
        return 0;
    }
}
public class Question {
    public static <T extends Comparable<T>> int test(T[] values) {
        return 8;
    }
    public static void main(String[] unused) {
        System.out.print(test(new A[] {}));
    }
}
""")
        Assert.assertEquals(classes.output, "8")
        Assert.assertTrue(classes.executed)
        Assert.assertFalse(classes.timedOut)
    }
}
