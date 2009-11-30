/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jexl2;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
/**
 * Test cases for the UnifiedEL.
 */
public class UnifiedJEXLTest extends JexlTestCase {
    private static final JexlEngine ENGINE = new JexlEngine();
    static {
        ENGINE.setLenient(false);
        ENGINE.setSilent(false);
        ENGINE.setCache(128);
    }
    private static final UnifiedJEXL EL = new UnifiedJEXL(ENGINE);
    private static final Log LOG = LogFactory.getLog(UnifiedJEXL.class);
    private JexlContext context = null;
    private Map<String,Object> vars =null;

    @Override
    public void setUp() throws Exception {
        // ensure jul logging is only error
        java.util.logging.Logger.getLogger(JexlEngine.class.getName()).setLevel(java.util.logging.Level.SEVERE);
        vars = new HashMap<String,Object>();
        context = new MapContext(vars);
    }

    @Override
    protected void tearDown() throws Exception {
        debuggerCheck(ENGINE);
        super.tearDown();
    }

    public static class Froboz {
        int value;
        public Froboz(int v) {
            value = v;
        }
        public void setValue(int v) {
            value = v;
        }
        public int getValue() {
            return value;
        }
        public int plus10() {
            int i = value;
            value += 10;
            return i;
        }
    }

    public UnifiedJEXLTest(String testName) {
        super(testName);
    }

    public void testStatement() throws Exception {
        vars.put("froboz", new Froboz(123));
        UnifiedJEXL.Expression check = EL.parse("${froboz.value = 32; froboz.plus10(); froboz.value}");
        Object o = check.evaluate(context);
        assertEquals("Result is not 42", new Integer(42), o);
    }

    public void testAssign() throws Exception {
        UnifiedJEXL.Expression assign = EL.parse("${froboz.value = 10}");
        UnifiedJEXL.Expression check = EL.parse("${froboz.value}");
        Object o = assign.evaluate(context);
        assertEquals("Result is not 10", new Integer(10), o);
        o = check.evaluate(context);
        assertEquals("Result is not 10", new Integer(10), o);
    }
    
    public void testComposite() throws Exception {
        UnifiedJEXL.Expression expr = EL.parse("Dear ${p} ${name};");
        vars.put("p", "Mr");
        vars.put("name", "Doe");
        assertTrue("expression should be immediate", expr.isImmediate());
        Object o = expr.evaluate(context);
        assertEquals("Dear Mr Doe;", o);
        vars.put("p", "Ms");
        vars.put("name", "Jones");
        o = expr.evaluate(context);
        assertEquals("Dear Ms Jones;", o);
    }

    public void testPrepareEvaluate() throws Exception {
        UnifiedJEXL.Expression expr = EL.parse("Dear #{p} ${name};");
        assertTrue("expression should be deferred", expr.isDeferred());
        vars.put("name", "Doe");
        UnifiedJEXL.Expression  phase1 = expr.prepare(context);
        String as = phase1.asString();
        assertEquals("Dear #{p} Doe;", as);
        vars.put("p", "Mr");
        vars.put("name", "Should not be used in 2nd phase");
        Object o = phase1.evaluate(context);
        assertEquals("Dear Mr Doe;", o);
    }
    
    public void testNested() throws Exception {
        UnifiedJEXL.Expression expr = EL.parse("#{${hi}+'.world'}");
        vars.put("hi", "hello");
        vars.put("hello.world", "Hello World!");
        Object o = expr.evaluate(context);
        assertTrue("source should not be expression", expr.getSource() != expr.prepare(context));
        assertTrue("expression should be deferred", expr.isDeferred());
        assertEquals("Hello World!", o);
    }

    public void testImmediate() throws Exception {
        JexlContext none = null;
        UnifiedJEXL.Expression expr = EL.parse("${'Hello ' + 'World!'}");
        assertTrue("prepare should return same expression", expr.prepare(none) == expr);
        Object o = expr.evaluate(none);
        assertTrue("expression should be immediate", expr.isImmediate());
        assertEquals("Hello World!", o);
    }

    public void testConstant() throws Exception {
        JexlContext none = null;
        UnifiedJEXL.Expression expr = EL.parse("Hello World!");
        assertTrue("prepare should return same expression", expr.prepare(none) == expr);
        Object o = expr.evaluate(none);
        assertTrue("expression should be immediate", expr.isImmediate());
        assertEquals("Hello World!", o);
    }

    public void testDeferred() throws Exception {
        JexlContext none = null;
        UnifiedJEXL.Expression expr = EL.parse("#{'world'}");
        assertTrue("prepare should return same expression", expr.prepare(none) == expr);
        Object o = expr.evaluate(none);
        assertTrue("expression should be deferred", expr.isDeferred());
        assertEquals("world", o);
    }

    public void testEscape() throws Exception {
        JexlContext none = null;
        UnifiedJEXL.Expression expr;
        Object o;
        // $ and # are escapable in UnifiedJEXL
        expr = EL.parse("\\#{'world'}");
        o = expr.evaluate(none);
        assertEquals("#{'world'}", o);
        expr = EL.parse("\\${'world'}");
        o = expr.evaluate(none);
        assertEquals("${'world'}", o);
    }

    public void testEscapeString() throws Exception {
        UnifiedJEXL.Expression expr = EL.parse("\\\"${'world\\'s finest'}\\\"");
        JexlContext none = null;
        Object o = expr.evaluate(none);
        assertEquals("\"world's finest\"", o);
    }

    public void testNonEscapeString() throws Exception {
        UnifiedJEXL.Expression expr = EL.parse("c:\\some\\windows\\path");
        JexlContext none = null;
        Object o = expr.evaluate(none);
        assertEquals("c:\\some\\windows\\path", o);
    }

    public void testMalformed() throws Exception {
        try {
            UnifiedJEXL.Expression expr = EL.parse("${'world'");
            JexlContext none = null;
            expr.evaluate(none);
            fail("should be malformed");
        }
        catch(UnifiedJEXL.Exception xjexl) {
            // expected
            String xmsg = xjexl.getMessage();
            LOG.warn(xmsg);
        }
    }
    
    public void testMalformedNested() throws Exception {
        try {
            UnifiedJEXL.Expression expr = EL.parse("#{${hi} world}");
            JexlContext none = null;
            expr.evaluate(none);
            fail("should be malformed");
        }
        catch(UnifiedJEXL.Exception xjexl) {
            // expected
            String xmsg = xjexl.getMessage();
            LOG.warn(xmsg);
        }
    }
    
    public void testBadContextNested() throws Exception {
        try {
            UnifiedJEXL.Expression expr = EL.parse("#{${hi}+'.world'}");
            JexlContext none = null;
            expr.evaluate(none);
            fail("should be malformed");
        }
        catch(UnifiedJEXL.Exception xjexl) {
            // expected
            String xmsg = xjexl.getMessage();
            LOG.warn(xmsg);
        }
    }
    
    public void testCharAtBug() throws Exception {
        vars.put("foo", "abcdef");
        UnifiedJEXL.Expression expr = EL.parse("${foo.substring(2,4)/*comment*/}");
        Object o = expr.evaluate(context);
        assertEquals("cd", o);
        
        vars.put("bar", "foo");
        try {
            ENGINE.setSilent(true);
            expr = EL.parse("#{${bar}+'.charAt(-2)'}");
            expr = expr.prepare(context);
            o = expr.evaluate(context);
            assertEquals(null, o);
        }
        finally {
            ENGINE.setSilent(false);
        }

    }

}
