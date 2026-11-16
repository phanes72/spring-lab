# Java Development with Vim, Maven, and JDB

A practical Debian terminal guide for editing, building, running, and debugging Java applications without an IDE. Includes the `spring-lab` Spring Framework 5 XML dependency-injection experiment.

## 1. Which tool does what?

| Tool | Purpose |
| --- | --- |
| Vim | Edit Java, XML, properties, and POM files |
| `javac` / `java` | Compile and run plain Java programs |
| Maven (`mvn`) | Resolve dependencies and build multi-module projects |
| JDB (`jdb`) | Debug Java bytecode with breakpoints, stepping, and variable inspection |
| Git | Track and publish changes |

Vim does not itself run Java. Open a terminal alongside Vim, or use Vim's `:!` command to execute a shell command.

## 2. Check your environment

```bash
java -version
javac -version
mvn -version
jdb -version
git --version
```

The current `spring-lab` project compiles for **Java 17** (`maven.compiler.release=17`) and uses **Spring Framework 5.3.39**, not Spring Boot. For a different project, check its `pom.xml`, toolchain, and required JDK first.

## 3. Work in Vim

```bash
cd ~/ocp21/spring-lab
vim service/src/main/java/com/springlab/service/MessageService.java
```

Useful Vim commands:

| Command | Action |
| --- | --- |
| `i` | Enter insert mode |
| `Esc` | Return to normal mode |
| `:w` | Save |
| `:q` | Quit |
| `:wq` | Save and quit |
| `u` | Undo |
| `/pattern` | Search file |
| `:!mvn compile` | Run a shell command from Vim (in Vim's current working directory) |
| `:pwd` | Show Vim's working directory |

For large codebases, terminal tools such as `rg 'class MessageService' .` (ripgrep, if installed) and `find . -name '*.java'` help locate source files.

## 4. Compile and run a standalone Java application

Suppose `Hello.java` contains a class with `public static void main(String[] args)`.

```bash
javac -g Hello.java
java Hello
```

The `-g` flag includes debug information, useful for JDB. If the class declares a package, compile into a classes directory and run by fully qualified name:

```bash
mkdir -p classes
javac -g -d classes src/com/example/Hello.java
java -cp classes com.example.Hello
```

For projects with external JARs, multiple modules, or resources, use the build system rather than compiling every file with a simple `find` command.

## 5. Build a Maven application

From the root directory containing the parent `pom.xml`:

```bash
mvn validate             # Check project structure
mvn compile              # Compile modules
mvn install              # Build and install artifacts into ~/.m2/repository
mvn -q install           # Same goal with quieter output
mvn -pl service -am compile  # Build service and required reactor modules
```

`-q` means *quiet*. It suppresses routine Maven output; remove it while diagnosing errors.

`-pl` selects projects; `-am` also makes required projects in the current reactor.

### Run a Maven application

If your project has a main class, you can run it with the Maven Exec Plugin:

```bash
mvn -q -f swing-app/pom.xml exec:java \
  -Dexec.mainClass=com.springlab.app.ApplicationLauncher
```

`exec:java` runs the Java main class **inside Maven's JVM** with the plugin's dependency classpath. It does not compile the code automatically; build first if needed.

To debug with **JDB**, start a separate JVM with a classpath, as shown below. `exec:java` is not itself a JDB command.

## 6. Prepare the spring-lab runtime classpath for JDB

From `~/ocp21/spring-lab`:

```bash
mvn install -DskipTests
mvn -f swing-app/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=classpath.txt
```

The second command writes `swing-app/classpath.txt`, containing the dependency JAR paths. This project has a Maven parent with four modules: `core`, `service`, `app-config`, and `swing-app`.

**Important:** `classpath.txt` is generated machine-specific data, not source code. Add it to `.gitignore` if it is not already ignored.

## 7. Start JDB for spring-lab

From the repository root:

```bash
jdb -sourcepath service/src/main/java:swing-app/src/main/java \
  -classpath "swing-app/target/classes:$(cat swing-app/classpath.txt)" \
  com.springlab.app.ApplicationLauncher
```

Explanation:

- `-sourcepath` tells JDB where to display your Java source.
- `-classpath` tells Java where to find compiled classes and dependency JARs.
- The final argument is the fully qualified main class.
- The classpath separator on Linux is `:`.

At the `>` prompt, configure breakpoints **before** running:

```text
stop in org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.populateBean
run
```

Some methods are overloaded. A breakpoint on an overloaded method name may fail; use a non-overloaded method, an exact signature, or a line breakpoint instead.

## 8. JDB command reference

| JDB command | Purpose |
| --- | --- |
| `run` | Start the debugged application |
| `stop in fully.qualified.Class.method` | Break when a method is entered |
| `stop at fully.qualified.Class:LINE` | Break at a source line |
| `cont` | Continue until the next breakpoint |
| `next` | Execute the next source line without stepping into called methods |
| `step` | Step into method calls |
| `step up` | Run until the current method returns |
| `locals` | Show local variables and method arguments |
| `print expression` | Evaluate an expression |
| `dump expression` | Inspect an object's fields |
| `where` | Display the current thread's call stack |
| `list` | Show source around the current location |
| `clear` | List breakpoints; use a specific breakpoint to remove it |
| `exit` | Exit JDB |

**Caution:** JDB's `print` may evaluate methods with side effects. Inspecting fields is usually safer than calling arbitrary methods while debugging.

## 9. Our Spring injection experiment

We used a `MessageService` with an `@Autowired` `Environment` field:

```java
@Autowired
private Environment environment;
```

We stopped in Spring's bean creation lifecycle and observed the `messageService` bean:

```text
stop in org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.populateBean
run
locals
print beanName
```

The breakpoint also stopped for Spring's internal beans. We used `cont`, followed by `print beanName`, until it printed:

```text
beanName = "messageService"
```

At that point, the following confirmed that the field had not yet been injected:

```text
print bw.getWrappedInstance()
print ((com.springlab.service.MessageService)bw.getWrappedInstance()).environment
```

Expected at this early point:

```text
environment = null
```

We then set a breakpoint on the annotation injection processor:

```text
stop in org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties
cont
locals
```

At the `messageService` invocation, `locals` showed a `bean` argument of type `MessageService`. We stepped through with `next` and inspected:

```text
print ((com.springlab.service.MessageService)bean).environment
```

**What we observed:** At the start of `postProcessProperties()`, the field was `null`. After the injection step, it became a `StandardEnvironment` instance. In the Spring 5.3.39 version used in this lab, we saw that transition while stepping from approximately line 415 to line 416. **Line numbers can change across versions; follow the method and variables, not just line numbers.**

Finally:

```text
where
```

The stack showed a chain like:

```text
ApplicationLauncher.main
  -> ClassPathXmlApplicationContext.<init>
  -> AbstractApplicationContext.refresh
  -> AbstractBeanFactory.getBean
  -> AbstractAutowireCapableBeanFactory.createBean
  -> AbstractAutowireCapableBeanFactory.doCreateBean
  -> AbstractAutowireCapableBeanFactory.populateBean
  -> AutowiredAnnotationBeanPostProcessor.postProcessProperties
```

This demonstrated **when** field injection happens for a Spring-managed bean: after construction, during bean population.

## 10. Diagnose a null @Autowired field

A null field does **not**, by itself, prove Spring injection is broken. Investigate:

1. **Object ownership:** Was the instance obtained from Spring or constructed by `new`, reflection, or another framework?
2. **Bean registration:** Is the class actually registered in the relevant Spring context?
3. **Annotation processing:** Is `AutowiredAnnotationBeanPostProcessor` registered in that context?
4. **Context boundaries:** Are there separate Spring contexts, and is the expected bean in the one managing this instance?
5. **Lifecycle timing:** Are you inspecting the field before injection completes?
6. **Property configuration:** `Environment` being non-null does not guarantee that `application.properties` is present among its property sources. XML placeholder resolution and `Environment.getProperty(...)` need separate verification.

In our lab, we intentionally disabled annotation processing and saw `Environment: null`, even though Spring still created `MessageService`. Restoring annotation processing restored injection.

A separate pub/sub framework that loads an XML file such as `Generic.xml` **might** create objects or Spring contexts, but that is a hypothesis to verify with the framework's code or a call stack—not an established cause.

## 11. Save your work to GitHub

```bash
git status
git add README.md
git commit -m "Document Vim Maven and JDB workflow"
git push
```

The repository's `main` branch is already configured to track `origin/main` over SSH, so a normal `git push` should work when authentication is available.

## 12. Quick checklist for any new Java project

1. Find the main class and the project's build file (`pom.xml`, `build.gradle`, or other).
2. Check the required JDK version.
3. Open and edit source in Vim.
4. Build with the project's build tool.
5. Run using the appropriate runtime classpath or plugin.
6. For debugging, compile with debug symbols and launch JDB with the full runtime classpath.
7. Set breakpoints, `run`, inspect `locals`, `print`, and `where`.
8. Commit working changes with Git.

This guide is for command-line Java applications, including Maven-based Swing/Spring apps. Other Java systems (application servers, containers, special launch scripts, native libraries, or remote services) can require additional runtime arguments and environment setup.
