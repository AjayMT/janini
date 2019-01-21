import org.codehaus.commons.compiler.CompileException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Run a class method using Janino.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class SimpleCompiler extends Source {

    /**
     * Sources to compile.
     */
    public String[] sources;

    /**
     * Default class name to load.
     */
    public String className = "Question";

    /**
     * Default method name to run.
     */
    public String methodName = "main";

    /**
     * Method to run.
     */
    private transient Method method;

    /**
     * Create a new SimpleCompiler and set default fields.
     */
    SimpleCompiler() {
        super();
    }

    /**
     * Create a new SimpleCompiler with a given compiler.
     *
     * @param setCompiler the compiler to use
     */
    SimpleCompiler(final String setCompiler) {
        super();
        compiler = setCompiler;
    }

    /**
     * Get a map of sources for this snippet. FIXME.
     *
     * @return a map of sources for this snippet.
     */
    protected Map<String, String> sources() {
        Map<String, String> classSources = new HashMap<>();
        for (int i = 0; i < sources.length; ++i) {
            classSources.put(Integer.toString(i) + ".java", sources[i]);
        }

        return classSources;
    }

    /**
     * Create a new SimpleCompiler execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new SimpleCompiler object
     * @return new SimpleCompiler object initialized from the JSON string
     */
    public static SimpleCompiler received(final String json) {
        return (SimpleCompiler) received(json, SimpleCompiler.class);
    }

    /**
     * Try compiling with Janino.
     *
     * @return ClassLoader if compilation was successful
     * @throws CompileException thrown if compilation fails
     * @throws IOException      thrown if there was a problem converting the string to bytes
     */
    private ClassLoader compileWithJanino() throws CompileException, IOException {
        org.codehaus.janino.SimpleCompiler simpleCompiler =
                new org.codehaus.janino.SimpleCompiler();
        simpleCompiler.setPermissions(permissions);
        for (String source : sources) {
            simpleCompiler.cook(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
            simpleCompiler.setParentClassLoader(simpleCompiler.getClassLoader());
        }
        compiler = "Janino";
        return simpleCompiler.getClassLoader();
    }

    /**
     * Try compiling with the JDK compiler.
     *
     * @return ClassLoader if compilation was successful
     * @throws CompileException thrown if compilation fails
     * @throws IOException      thrown if there was a problem converting the string to bytes
     */
    private ClassLoader compileWithJDK() throws CompileException, IOException {
        org.codehaus.commons.compiler.jdk.SimpleCompiler simpleCompiler =
                new org.codehaus.commons.compiler.jdk.SimpleCompiler();
        simpleCompiler.setPermissions(permissions);
        for (String source : sources) {
            simpleCompiler.cook(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        }
        compiler = "JDK";
        return simpleCompiler.getClassLoader();
    }

    /**
     * Compile Java classes using Janino.
     * <p>
     * Throws an exception if compilation fails.
     *
     * @throws CompileException       if compilation fails
     * @throws IOException            if there is a problem creating the input stream
     * @throws ClassNotFoundException if the class specified is not found
     * @throws NoSuchMethodException  if the method specified is not found or is not static
     */
    public void doCompile() throws CompileException, IOException, ClassNotFoundException, NoSuchMethodException {
        ClassLoader classLoader;
        switch (compiler) {
            case "Janino":
                classLoader = compileWithJanino();
                break;
            case "JDK":
                classLoader = compileWithJDK();
                break;
            default:
                try {
                    classLoader = compileWithJanino();
                    break;
                } catch (CompileException ignored) { }
                classLoader = compileWithJDK();
                break;
        }
        Class<?> klass = classLoader.loadClass(className);
        method = klass.getMethod(methodName, String[].class);
        if (!(Modifier.isStatic(method.getModifiers()))) {
            throw new NoSuchMethodException(methodName + " must be static");
        }
    }

    /**
     * Execute our Java classes code.
     * <p>
     * Throws an exception if execution fails.
     *
     * @throws InvocationTargetException if the target cannot be invoked
     * @throws IllegalAccessException    if the code attempts to violate the sandbox
     */
    public void doExecute() throws InvocationTargetException, IllegalAccessException {
        method.invoke(null, (Object) new String[]{});
    }

    /**
     * Convenience method for testing.
     *
     * @param setSource set the source of the SimpleCompiler object
     * @return this object for chaining
     */
    public Source run(final String... setSource) {
        sources = setSource;
        return super.run();
    }
}
