/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018.  Georg Beier. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.geobe.util.vaadin.builder

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Created by georg beier on 20.03.2018.
 */
class MdMaker {
    def builder = new VaadinBuilder()
    def c = VaadinBuilder.C
    def f = VaadinBuilder.F

    def vaadinApiVersion = "8.3/8.3.2"
    def vaadinApiUrl = "https://vaadin.com/download/release/$vaadinApiVersion/docs/api/"

    def fields = builder.fields
    def containers = builder.containers
    def loader = this.class.classLoader

    def base = new File('.').canonicalPath
            .replaceFirst(/src.*/, '') + 'wiki' + File.separator


    String currentKeyword
    Class currentComponent
    String currentUrl
    Method currentMethod

    private indexHeader = """
## VaadinBuilder Component Index

VaadinBuilder components **are** Vaadin components (with one exception)! 
They are just constructed and parametrized declaratively. 
They are pooled in VaadinBuilder into two groups, containers 
(aka [Layout Components]\
(https://vaadin.com/docs/v8/framework/layout/layout-overview.html) )
and fields (aka [Server-Side Components]\
(https://vaadin.com/docs/v8/framework/components/components-overview.html)).

A special component is `subtree`. It is used to include a whole component
subtree that is defined in a separate class.

### Subtree Component

Key | Class Documentation
----|----
[subtree](SubtreeDetails)| \
[SubTree](https://geobe.github.io/vaadin-builder-doc/index.html?de/geobe/util/vaadin/builder/SubTree.html)

### Layout Components

Key | Vaadin Component
----|----
${buildComponentsTable(VaadinBuilder.C)}

### Field Components

Key | Vaadin Component
----|----
${buildComponentsTable(VaadinBuilder.F)}
"""

    /**
     * Build an index page for VaadinBuilder supported components in markdown format.
     */
    void makeComponentIndex() {
        def mdf = new File(base + 'Components.md')
        mdf.withWriter('utf-8') { writer ->
            writer.write indexHeader
        }
    }

    void makeComponentPages() {
        c.enumConstants*.toString().each {
            buildComponentPage(it)
        }
        f.enumConstants*.toString().grep{it != 'subtask'}.each {
            buildComponentPage(it)
        }
    }

    private buildComponentPage(String key){
        def path = fields[key] ?: containers[key]
        if(path) {
            try {
                def component = loader.loadClass(path)
                def url = linkForClass(component)
                def mdf = new File(base + "components/${key.capitalize()}Details.md")
                mdf.withWriter('utf-8') { writer ->
                    writer.write componentHead(key, component, url)
                    writeLinesForMethods(writer, key, component)
                }
            } catch (Exception ex) {
                println(ex)
            }
        }
    }


    private buildComponentsTable(Class<? extends Enum> e) {
        StringBuffer list = new StringBuffer()
        e.enumConstants*.toString().each { idx ->
            if(idx != 'subtree') {
                def path = fields[idx] ?: containers[idx]
                if(path) {
                    try {
                        def clazz = loader.loadClass(path)
                        list.append "[$idx](components/${idx.capitalize()}Details) | "
                        list.append "${linkForClass(clazz)}\n"
                    } catch (Exception ex) {
                        println(ex)
                    }
                }
            }
        }
        list.toString()
    }

    private writeLinesForMethods(Writer writer, String key, Class clazz) {
        for (Class aClass = clazz; aClass; aClass = aClass.superclass) {
            if (!aClass.name.startsWith('com.vaadin'))
                break
            def props = propertiesForClass(aClass)
            if (props) {
                props.each {
                    writer.write"${lineForMethod(it)}"
                }
            }
        }
    }

    private lineForMethod(Map<Method, Class> m) {
        def method = m.keySet().first()
        def methodClassLink = method.declaringClass.canonicalName.replaceAll(/\./, '/')
        def methodName = method.name
        def methodProp = methodName.replaceFirst(/.../, '').uncapitalize()
        def param = m[method]
        def paramtype = param.toString()
        def paramLink = paramtype.replaceFirst(/.* /, '').replace('$', '.')
        String type
        switch (paramtype) {
            case ~/interface.*/:
                type = 'Closure'
                break
            case ~/class \[L.*/:
                def cl = paramtype.replaceFirst(/.*\[L/, '')
                        .replace(';', '')
                        .replace('$', '.')
                type = "[${cl}]"
                paramLink = cl == 'java.lang.Object' ? 'T...' : "${cl}..."
                break
            case ~/class .*/:
                def cl
                paramtype.eachMatch(/class [a-z.]*(\w*)/) { a, b ->
                    cl = b
                }
                type = "${cl}"
                break
            case ~/com\.vaadin\..*/:
                if (paramtype.contains('$')) {
                    def parts = paramtype.split(/\$/)
                    def base = parts[0]
                    def t = base.replaceFirst('[a-z.]*', '')
                    t = t.replaceFirst(/\..*/, '')
                    def l = paramtype.replace('.', '/').replaceAll(/<\w*>/, '')
                    def sub = parts[1].replaceFirst('[a-z.]*', '')
                    type = "[$t]($l.${sub}.html)"
                } else {
                    def t = paramtype.replaceFirst('[a-z.]*', '')
                    def l = paramtype.replace('.', '/').replaceAll(/<\w*>/, '')
                    type = "[$t](${l}.html)"
                }
                break
            case ~/[a-z]+/:
                type = paramtype
                break
            default:
                type = 'unknown'
        }
        "$methodProp | $type | [$methodName]($vaadinApiUrl${methodClassLink}.html#$methodName-${paramLink?:''}-)\n"
    }

    private propertiesForClass(Class clazz) {
        def mp =
                clazz.declaredMethods.grep {
                    Method it ->
                        it.modifiers & Modifier.PUBLIC && (it.name ==~ /set.*/ || it.name ==~ /add.*/)
                }.collect {
                    if (it.parameterCount == 0) {
                        [(it): null]
                    } else if (it.parameterCount == 1) {
                        def pc = it.parameterTypes[0]
                        [(it): pc]
                    } else {
                        [:]
                    }
                }.grep().sort { it.keySet().first().name }
        mp
    }


    private linkForClass(Class clazz) {
        "[${clazz.name.replaceFirst(/[a-z.]*/, '')}]" +
                "($vaadinApiUrl/index.html?${clazz.name.replace('.', '/')}.html)"
    }

    private String componentHead(keyword, component, url) {
"""## VaadinBuilder Component Documentation

### Supported Field Properties for Key **$keyword**

$keyword || $url 
----|----|----
__Property__ | __Value Type__ | __Vaadin Doc__
"""
    }
}
