package com.parigan.modelasserter.core

import groovy.io.FileType
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Modifier

/***
 * The recursive collection of all non-test source code is done in this spec. Use it & extend it as neeed.
 * Don't duplicate this recursion elsewhere for .groovy or .java's. It's too costly (time-factor).
 * If I missed another spec that recursed the file system, we should refactor/merge Specs.
 * Goal: keep the recursive introspection of production source code to one instance/thread. Currently this spec increases
 * Maven test target by 53-seconds.
 */

@Ignore
class ObjectModelSpec extends Specification {
    static List<String> IGNORE_CLASS_NAMES = ["WriteEntityNameTag"]
    //classes that bomb on Groovy/Java classLoad + not really important for unit testing

    @Shared
    List<String> prodAndTestSourceCodeFiles
    @Shared
    List<String> prodSourceCodeFiles
    @Shared
    List<File> abstractBaseBenefitSubClasses
    @Shared
    Map<String, String> badAbstractBaseBenefitSubClasses

    @Shared
    List<File> listOfBadSubClasses

    def setupSpec() {
        prodAndTestSourceCodeFiles = []
        prodSourceCodeFiles = []
        listOfBadSubClasses = []
        abstractBaseBenefitSubClasses = []
        badAbstractBaseBenefitSubClasses = [:]

        allSourceCodeFiles() { prodAndTestSourceCodeFiles << it }
        reduceToProdOnly()
        superClassIntrospector()
    }

    def "AbstractBaseBenefit sub classes have overriden afterAdd/afterUpdate/afterDelete with setBenefitPlanInvalid"() {
        expect:
        badAbstractBaseBenefitSubClasses.size() == 0
    }

    /**
     * displaying bad files at cleanupSpec is faster than unrolling individual specs (maximizing Maven-test target speed).
     */
    def cleanupSpec() {
        if (badAbstractBaseBenefitSubClasses.size() != 0) {
            println("Found ${badAbstractBaseBenefitSubClasses.size()} missing method(s)")
            badAbstractBaseBenefitSubClasses.each { classMethodName, errorMessage -> println(errorMessage) }
        }
    }

    private void abstractBaseBenefitHasOverrides(Class<?> aClass) {
        if (!Modifier.isAbstract(aClass.modifiers)) {
            checkMethodOverriden(aClass, "afterAdd")
            checkMethodOverriden(aClass, "afterUpdate")
            checkMethodOverriden(aClass, "afterDeleteOnCached")
        } else {
            //recurse - todo
        }
    }

    void checkMethodOverriden(Class<?> aClass, String methodName) {
        try {
            aClass.getMethod(methodName)
        } catch (NoSuchMethodException e) {
            badAbstractBaseBenefitSubClasses.put("${aClass.name}.${methodName}()", e.message)
        }
    }

    def cleanup() {
        ApplicationContextHolder.clearApplicationContext()
    }

    private void superClassIntrospector() {
        prodSourceCodeFiles.each { i ->
            if (i.toString().contains(".groovy")) {
//                println("${i} -super:=${groovyClassResolver(i).superclass}")
                if (groovyClassResolver(i).superclass == AbstractBaseBenefit.class) {
                    abstractBaseBenefitSubClasses << i
                }
            }
            if (i.toString().contains(".java")) {
//                println("${i} -super:=${getJavaSuperClass(javaClassResolver(i))}")
                if (getJavaSuperClass(javaClassResolver(i)) == AbstractBaseBenefit.class) {
                    abstractBaseBenefitHasOverrides(javaClassResolver(i))
                }
            }
        }
    }


    private void reduceToProdOnly() {
        prodAndTestSourceCodeFiles.each { i ->
            if (!i.toString().contains("test") && !IGNORE_CLASS_NAMES.contains(toClassName(i))) {
                prodSourceCodeFiles << i
            }
        }
    }

    private static String toClassName(File f) {
        int use
        int mac = f.toString().lastIndexOf("/")
        int windows = f.toString().lastIndexOf("\\")
        if (mac > windows) {
            use = mac
        } else {
            use = windows
        }
        return f.toString().substring(use + 1, f.toString().indexOf("."))
    }


    def allSourceCodeFiles(Closure closure) {
        new File(new File(".").getCanonicalPath()).eachFileRecurse(FileType.FILES) {
            if (it.name =~ /\.groovy/ || it.name =~ /\.java/) {
                closure.call(it)
            }
        }
    }

    private static Class<?> getJavaSuperClass(Class<?> aClass) {
        try {
            return aClass.superclass
        } catch (ClassFormatError | Exception ex) {
            println("getSuperClass(${aClass}) threw an exception - $ex.message")
            System.exit(-1)
        }
    }

    private static Class<?> groovyClassResolver(File file) {
        GroovyCodeSource gCode = new GroovyCodeSource(file)
        GroovyClassLoader gClass = new GroovyClassLoader()
        try {
            return gClass.parseClass(gCode, true)
        } catch (ClassFormatError classFormatEx) {
            println("Groovy file $file.name threw an exception - $classFormatEx.message")
            System.exit(-1)
        }
    }

    private static Class<?> javaClassResolver(File file) {
        try {
            return Class.forName(file.toString().substring(file.toString().indexOf("com"), file.toString().indexOf(".java")).replace('\\', '.').replace('/', '.'))
        } catch (ClassFormatError | Exception ex) {
            println("Java file $file.name threw an exception - $ex.message")
            System.exit(-1)
        }
    }
}
