import org.codehaus.commons.compiler.CompileException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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
     * Extract the name of a class from a class declaration token.
     *
     * @param token the token to parse
     * @return the name of the class being declared
     */
    private String parseClassDeclaration(final String token) {
        return token.split(Pattern.quote("{"))[0].split(Pattern.quote("<"))[0];
    }

    /**
     * Parse a single string of code and split it into individual classes.
     *
     * @param source the source code to parse
     * @return a map of class names to class code within source
     */
    private Map<String, String> parseClasses(final String source) {
        final List<String> accessModifiers = Arrays.asList(new String[] {
                "public", "private", "protected"
            });

        Map<String, String> classes = new HashMap<>();
        String[] lines = source.split("\\r?\\n");
        List<Integer> lineNumbers = new ArrayList<>();
        List<Integer> packageLineNumbers = new ArrayList<>();
        String packageSource = "";

        // construct list of line numbers of class declarations
        for (int lineNumber = 0; lineNumber < lines.length; ++lineNumber) {
            String line = new String(lines[lineNumber]);
            line.trim();
            List<String> tokens = Arrays.asList(line.split(" "))
                .stream()
                .filter(s -> s.length() > 0)
                .collect(Collectors.toList());

            if (tokens.size() == 0) {
                continue;
            }

            boolean isClass = tokens.get(0).equals("class");
            if (tokens.size() > 1) {
                isClass = isClass
                    || (tokens.get(1).equals("class")
                        && accessModifiers.contains(tokens.get(0)));
            }

            boolean isPackageLine = tokens.get(0).equals("import")
                || tokens.get(0).equals("package");

            if (isClass) {
                lineNumbers.add(lineNumber);
            } else if (isPackageLine) {
                packageLineNumbers.add(lineNumber);
            }
        }

        // assemble package lines (i.e import and package statements)
        for (int lineNumber : packageLineNumbers) {
            packageSource += lines[lineNumber] + "\n";
        }

        // construct map of class names to class source code
        for (int lineIndex = 0; lineIndex < lineNumbers.size(); ++lineIndex) {
            int current = lineNumbers.get(lineIndex);
            int next = lines.length;

            if (lineIndex < lineNumbers.size() - 1) {
                next = lineNumbers.get(lineIndex + 1);
            }

            String classSource = String.join("\n", Arrays.copyOfRange(lines, current, next));
            String classNameLocal = "";
            List<String> tokens = Arrays.asList(lines[current].split(" "))
                .stream()
                .filter(s -> s.length() > 0)
                .collect(Collectors.toList());

            for (int tokenIndex = 0; tokenIndex < tokens.size() - 1; ++tokenIndex) {
                if (tokens.get(tokenIndex).equals("class")) {
                    classNameLocal = parseClassDeclaration(tokens.get(tokenIndex + 1));
                }
            }

            classes.put(classNameLocal, packageSource + classSource);
        }

        return classes;
    }

    /**
     * Get a map of sources for this snippet. FIXME.
     *
     * @return a map of sources for this snippet.
     */
    protected Map<String, String> sources() {
        Map<String, String> classSources = new HashMap<>();

        for (String source : sources) {
            Map<String, String> classes = parseClasses(source);
            for (Map.Entry<String, String> klass : classes.entrySet()) {
                classSources.put(klass.getKey() + ".java", klass.getValue());
            }
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
