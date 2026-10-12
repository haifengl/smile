import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Prints the binary names of all Serializable classes (not interfaces) found in the given class directories.
 * Usage: java ListSerializable.java &lt;classpath&gt; &lt;classesDir&gt;...
 */
public class ListSerializable {
    public static void main(String[] args) throws IOException {
        List<URL> urls = new ArrayList<>();
        for (String p : args[0].split(java.io.File.pathSeparator)) urls.add(Path.of(p).toUri().toURL());
        var loader = new URLClassLoader(urls.toArray(URL[]::new), ListSerializable.class.getClassLoader());
        var names = new TreeSet<String>();
        for (int i = 1; i < args.length; i++) {
            Path root = Path.of(args[i]);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    String rel = root.relativize(f).toString();
                    if (rel.equals("module-info.class") || rel.endsWith("package-info.class")) continue;
                    String name = rel.substring(0, rel.length() - 6).replace('/', '.');
                    try {
                        Class<?> c = Class.forName(name, false, loader);
                        if (!c.isInterface() && !c.isAnnotation() && Serializable.class.isAssignableFrom(c)) names.add(name);
                    } catch (Throwable t) {
                        System.err.println("skip " + name + ": " + t);
                    }
                }
            }
        }
        names.forEach(System.out::println);
    }
}
