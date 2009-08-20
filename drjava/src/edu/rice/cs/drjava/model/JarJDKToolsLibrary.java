/*BEGIN_COPYRIGHT_BLOCK
 *
 * Copyright (c) 2001-2009, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initative Approved is a trademark of the Open Source Initiative.
 * 
 * This file is part of DrJava.  Download the current version of this project
 * from http://www.drjava.org/ or http://sourceforge.net/projects/drjava/
 * 
 * END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.model;

import java.io.File;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.io.IOException;

import edu.rice.cs.plt.iter.IterUtil;
import edu.rice.cs.plt.io.IOUtil;
import edu.rice.cs.plt.lambda.LambdaUtil;
import edu.rice.cs.plt.lambda.Predicate;
import edu.rice.cs.plt.reflect.ReflectUtil;
import edu.rice.cs.plt.reflect.PathClassLoader;
import edu.rice.cs.plt.reflect.ShadowingClassLoader;
import edu.rice.cs.plt.reflect.PreemptingClassLoader;
import edu.rice.cs.plt.reflect.ReflectException;
import edu.rice.cs.plt.reflect.JavaVersion;
import edu.rice.cs.plt.reflect.JavaVersion.FullVersion;

import edu.rice.cs.drjava.model.compiler.CompilerInterface;
import edu.rice.cs.drjava.model.compiler.NoCompilerAvailable;
import edu.rice.cs.drjava.model.debug.Debugger;
import edu.rice.cs.drjava.model.debug.NoDebuggerAvailable;
import edu.rice.cs.drjava.model.javadoc.JavadocModel;
import edu.rice.cs.drjava.model.javadoc.DefaultJavadocModel;
import edu.rice.cs.drjava.model.javadoc.NoJavadocAvailable;

/** A JDKToolsLibrary that was loaded from a specific jar file. */
public class JarJDKToolsLibrary extends JDKToolsLibrary {
  
  /** Packages to shadow when loading a new tools.jar.  If we don't shadow these classes, we won't
    * be able to load distinct versions for each tools.jar library.  These should be verified whenever
    * a new Java version is released.  (We can't just shadow *everything* because some classes, at 
    * least in OS X's classes.jar, can only be loaded by the JVM.)
    */
  private static final Iterable<String> TOOLS_PACKAGES = IterUtil.asIterable(new String[]{
      // From 1.4 tools.jar:
      "com.sun.javadoc",
      "com.sun.jdi",
      "com.sun.tools",
      "sun.applet", // also bundled in rt.jar
      "sun.rmi.rmic",
      //"sun.security.tools", // partially bundled in rt.jar -- it's inconsistent between versions, so we need to
                              // allow these classes to be loaded.  Hopefully this doesn't break anything.
      "sun.tools", // sun.tools.jar, sun.tools.hprof, and (sometimes) sun.tools.util.CommandLine are also in rt.jar
    
      // Additional from 5 tools.jar:
      "com.sun.jarsigner",
      "com.sun.mirror",
      "sun.jvmstat",
    
      // Additional from 6 tools.jar:
      "com.sun.codemodel",
      "com.sun.istack.internal.tools", // other istack packages are in rt.jar
      "com.sun.istack.internal.ws",
      "com.sun.source",
      "com.sun.xml.internal.dtdparser", // other xml.internal packages are in rt.jar
      "com.sun.xml.internal.rngom",
      "com.sun.xml.internal.xsom",
      "org.relaxng"
  });

  
  private final File _location;
  
  private JarJDKToolsLibrary(File location, FullVersion version, CompilerInterface compiler, Debugger debugger,
                             JavadocModel javadoc) {
    super(version, compiler, debugger, javadoc);
    _location = location;
  }
  
  public File location() { return _location; }
  
  public String toString() { return super.toString() + " at " + _location; }
  
  /** Create a JarJDKToolsLibrary from a specific {@code "tools.jar"} or {@code "classes.jar"} file. */
  public static JarJDKToolsLibrary makeFromFile(File f, GlobalModel model) {
    FullVersion version = guessVersion(f);
    CompilerInterface compiler = NoCompilerAvailable.ONLY;
    Debugger debugger = NoDebuggerAvailable.ONLY;
    JavadocModel javadoc = new NoJavadocAvailable(model);
    
    // We can't execute code that was possibly compiled for a later Java API version.
    if (JavaVersion.CURRENT.supports(version.majorVersion())) {
      // block tools.jar classes, so that references don't point to a different version of the classes
      ClassLoader loader =
        new ShadowingClassLoader(JarJDKToolsLibrary.class.getClassLoader(), true, TOOLS_PACKAGES, true);
      Iterable<File> path = IterUtil.singleton(IOUtil.attemptAbsoluteFile(f));
      
      String compilerAdapter = adapterForCompiler(version.majorVersion());
      if (compilerAdapter != null) {
        
        // determine boot class path
        File libDir = null;
        if (f.getName().equals("classes.jar")) { libDir = f.getParentFile(); }
        else if (f.getName().equals("tools.jar")) {
          File jdkLibDir = f.getParentFile();
          if (jdkLibDir != null) {
            File jdkRoot = jdkLibDir.getParentFile();
            if (jdkRoot != null) {
              File jreLibDir = new File(jdkRoot, "jre/lib");
              if (IOUtil.attemptExists(new File(jreLibDir, "rt.jar"))) { libDir = jreLibDir; }
            }
            if (libDir == null) {
              if (IOUtil.attemptExists(new File(jdkLibDir, "rt.jar"))) { libDir = jdkLibDir; }
            }
          }
        }
        List<File> bootClassPath = null; // null defers to the compiler's default behavior
        if (libDir != null) {
          File[] jars = IOUtil.attemptListFiles(libDir, IOUtil.extensionFilePredicate("jar"));
          if (jars != null) { bootClassPath = Arrays.asList(jars); }
        }

        try {
          Class<?>[] sig = { FullVersion.class, String.class, List.class };
          Object[] args = { version, f.toString(), bootClassPath };
          CompilerInterface attempt = (CompilerInterface) ReflectUtil.loadLibraryAdapter(loader, path, compilerAdapter, 
                                                                                         sig, args);
          if (attempt.isAvailable()) { compiler = attempt; }
        }
        catch (ReflectException e) { /* can't load */ }
        catch (LinkageError e) { /* can't load */ }
      }
      
      String debuggerAdapter = adapterForDebugger(version.majorVersion());
      String debuggerPackage = "edu.rice.cs.drjava.model.debug.jpda";
      if (debuggerAdapter != null) {
        try {
          Class<?>[] sig = { GlobalModel.class };
          // can't use loadLibraryAdapter because we need to preempt the whole package
          ClassLoader debugLoader = new PreemptingClassLoader(new PathClassLoader(loader, path), debuggerPackage);
          Debugger attempt = (Debugger) ReflectUtil.loadObject(debugLoader, debuggerAdapter, sig, model);        
          if (attempt.isAvailable()) { debugger = attempt; }
        }
        catch (ReflectException e) { /* can't load */ }
        catch (LinkageError e) { /* can't load */ }
      }
      
      try {
        new PathClassLoader(loader, path).loadClass("com.sun.tools.javadoc.Main");
        File bin = new File(f.getParentFile(), "../bin");
        if (!IOUtil.attemptIsDirectory(bin)) { bin = new File(f.getParentFile(), "../Home/bin"); }
        if (!IOUtil.attemptIsDirectory(bin)) { bin = new File(System.getProperty("java.home", f.getParent())); }
        javadoc = new DefaultJavadocModel(model, bin, path);
      }
      catch (ClassNotFoundException e) { /* can't load */ }
      catch (LinkageError e) { /* can't load (probably not necessary, but might as well catch it) */ }
        
    }
    
    return new JarJDKToolsLibrary(f, version, compiler, debugger, javadoc);
  }
  
  private static FullVersion guessVersion(File f) {
    FullVersion result = null;

    // We could start with f.getParentFile(), but this simplifies the logic
    File current = IOUtil.attemptCanonicalFile(f);
    do {
      String name = current.getName();
      if (name.startsWith("jdk")) { result = JavaVersion.parseFullVersion(name.substring(3)); }
      else if (name.startsWith("j2sdk")) { result = JavaVersion.parseFullVersion(name.substring(5)); }
      else if (name.matches("\\d+\\.\\d+\\.\\d+")) { result = JavaVersion.parseFullVersion(name); }
      current = current.getParentFile();
    } while (current != null && result == null);
    
    if (result == null || result.majorVersion().equals(JavaVersion.UNRECOGNIZED)) {
      JarFile jf = null;
      try {
        jf = new JarFile(f);
        Manifest mf = jf.getManifest();
        String v = mf.getMainAttributes().getValue("Created-By");
        if (v != null) {
          int space = v.indexOf(' ');
          if (space>=0) v = v.substring(0,space);
          result = JavaVersion.parseFullVersion(v);
        }
      }
      catch(IOException ioe) { result = null; }
      finally {
        try {
          if (jf != null) jf.close();
        }
        catch(IOException ioe) { /* ignore, just trying to close the file */ }
      }
      if (result == null || result.majorVersion().equals(JavaVersion.UNRECOGNIZED)) {
        // Couldn't find a good version number, so we'll just guess that it's the currently-running version
        // Useful where the tools.jar file is in an unusual custom location      
        result = JavaVersion.CURRENT_FULL;
      }
    }
    return result;
  }
  
//  // Lifted from DrJava.java; may be a useful alternative to the path-based approach of guessVersion.
//  /** @return a string with the suspected version of the tools.jar file, or null if an error occurred. */
//  private static String _getToolsJarVersion(File toolsJarFile) {
//    try {
//      JarFile jf = new JarFile(toolsJarFile);
//      Manifest mf = jf.getManifest();
//      ByteArrayOutputStream baos = new ByteArrayOutputStream();
//      mf.write(baos);
//      String str = baos.toString();
//      // the expected format of str is:
//      // Manifest-Version: 1.0
//      // Created-By: 1.5.0_07 (Sun Microsystems Inc.)
//      //
//      final String CB = "Created-By: ";
//      int beginPos = str.indexOf(CB);
//      if (beginPos >= 0) {
//        beginPos += CB.length();
//        int endPos = str.indexOf(StringOps.EOL, beginPos);
//        if (endPos >= 0) return str.substring(beginPos, endPos);
//        else {
//          endPos = str.indexOf(' ', beginPos);
//          if (endPos >= 0) return str.substring(beginPos, endPos);
//          else {
//            endPos = str.indexOf('\t', beginPos);
//            if (endPos >= 0) return str.substring(beginPos, endPos);
//          }
//        }
//      }
//    }
//    catch(Exception rte) { /* ignore, just return null */ }
//    return null;
//  }
  
  /** Produce a list of tools libraries discovered on the file system.  A variety of locations are searched;
   * only those files that can produce a valid library (see {@link #isValid} are returned.  The result is
   * sorted by version.  Where one library of the same version might be preferred over another, the preferred 
   * library appears earlier in the result list.
   */
  public static Iterable<JarJDKToolsLibrary> search(GlobalModel model) {
    String javaHome = System.getProperty("java.home");
    String envJavaHome = null;
    String programFiles = null;
    String systemDrive = null;
    if (JavaVersion.CURRENT.supports(JavaVersion.JAVA_5)) {
      // System.getenv is deprecated under 1.3 and 1.4, and may throw a java.lang.Error (!),
      // which we'd rather not have to catch
      envJavaHome = System.getenv("JAVA_HOME");
      programFiles = System.getenv("ProgramFiles");
      systemDrive = System.getenv("SystemDrive");
    }
    
    /* roots is a list of possible parent directories of Java installations; we want to eliminate duplicates & 
     * remember insertion order
     */
    LinkedHashSet<File> roots = new LinkedHashSet<File>();
    
    if (javaHome != null) {
      addIfDir(new File(javaHome), roots);
      addIfDir(new File(javaHome, ".."), roots);
      addIfDir(new File(javaHome, "../.."), roots);
    }
    if (envJavaHome != null) {
      addIfDir(new File(envJavaHome), roots);
      addIfDir(new File(envJavaHome, ".."), roots);
      addIfDir(new File(envJavaHome, "../.."), roots);
    }
    
    if (programFiles != null) {
      addIfDir(new File(programFiles, "Java"), roots);
      addIfDir(new File(programFiles), roots);
    }
    addIfDir(new File("/C:/Program Files/Java"), roots);
    addIfDir(new File("/C:/Program Files"), roots);
    if (systemDrive != null) {
      addIfDir(new File(systemDrive, "Java"), roots);
      addIfDir(new File(systemDrive), roots);
    }
    addIfDir(new File("/C:/Java"), roots);
    addIfDir(new File("/C:"), roots);
    
    addIfDir(new File("/System/Library/Frameworks/JavaVM.framework/Versions"), roots);
    
    addIfDir(new File("/usr/java"), roots);
    addIfDir(new File("/usr/j2se"), roots);
    addIfDir(new File("/usr"), roots);
    addIfDir(new File("/usr/local/java"), roots);
    addIfDir(new File("/usr/local/j2se"), roots);
    addIfDir(new File("/usr/local"), roots);

    /* Entries for Linux java packages */
    addIfDir(new File("/usr/lib/jvm"), roots);
    addIfDir(new File("/usr/lib/jvm/java-6-sun"), roots);
    addIfDir(new File("/usr/lib/jvm/java-1.5.0-sun"), roots);
    addIfDir(new File("/usr/lib/jvm/java-6-openjdk"), roots);

    addIfDir(new File("/home/mgricken/usr/lib/jvm"), roots);
    addIfDir(new File("/home/mgricken/usr/lib/jvm/java-6-sun"), roots);
    addIfDir(new File("/home/mgricken/usr/lib/jvm/java-1.5.0-sun"), roots);
    addIfDir(new File("/home/mgricken/usr/lib/jvm/java-6-openjdk"), roots);
    addIfDir(new File("/home/javaplt/java/Linux-i686"), roots);

    /* jars is a list of possible tools.jar (or classes.jar) files; we want to eliminate duplicates & 
     * remember insertion order
     */
    LinkedHashSet<File> jars = new LinkedHashSet<File>();
    // matches: starts with "j2sdk", starts with "jdk", has form "[number].[number].[number]" (OS X), starts with "java-" (Linux)
    Predicate<File> subdirFilter = LambdaUtil.or(IOUtil.regexCanonicalCaseFilePredicate("j2sdk.*"),
                                                 IOUtil.regexCanonicalCaseFilePredicate("jdk.*"),
                                                 LambdaUtil.or(IOUtil.regexCanonicalCaseFilePredicate("\\d+\\.\\d+\\.\\d+"),
                                                               IOUtil.regexCanonicalCaseFilePredicate("java.*")));
    for (File root : roots) {
      for (File subdir : IOUtil.attemptListFilesAsIterable(root, subdirFilter)) {
        addIfFile(new File(subdir, "lib/tools.jar"), jars);
        addIfFile(new File(subdir, "Classes/classes.jar"), jars);
      }
    }
    
    // We store everything in reverse order, since that's the natural order of the versions
    Map<FullVersion, Iterable<JarJDKToolsLibrary>> results = 
      new TreeMap<FullVersion, Iterable<JarJDKToolsLibrary>>();
    for (File jar : jars) {
      JarJDKToolsLibrary lib = makeFromFile(jar, model);
      if (lib.isValid()) {
        FullVersion v = lib.version();
        if (results.containsKey(v)) { results.put(v, IterUtil.compose(lib, results.get(v))); }
        else { results.put(v, IterUtil.singleton(lib)); }
      }
    }
    return IterUtil.reverse(IterUtil.collapse(results.values()));
  }
  
  /** Add a canonicalized {@code f} to the given set if it is an existing directory */
  private static void addIfDir(File f, Set<? super File> set) {
    f = IOUtil.attemptCanonicalFile(f);
    if (IOUtil.attemptIsDirectory(f)) { set.add(f); }
  }
  
  /** Add a canonicalized {@code f} to the given set if it is an existing file */
  private static void addIfFile(File f, Set<? super File> set) {
    f = IOUtil.attemptCanonicalFile(f);
    if (IOUtil.attemptIsFile(f)) { set.add(f); }
  }
  
}
