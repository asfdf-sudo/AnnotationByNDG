/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE476_NULL_Pointer_Dereference__String_61b.java
Label Definition File: CWE476_NULL_Pointer_Dereference.label.xml
Template File: sources-sinks-61b.tmpl.java
*/
/*
 * @description
 * CWE: 476 Null Pointer Dereference
 * BadSource:  Set data to null
 * GoodSource: Set data to a non-null value
 * Sinks:
 *    GoodSink: add check to prevent possibility of null dereference
 *    BadSink : possibility of null dereference
 * Flow Variant: 61 Data flow: data returned from one method to another in different classes in the same package
 *
 * */

package testcases.CWE476_NULL_Pointer_Dereference;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import testcasesupport.*;

public class CWE476_NULL_Pointer_Dereference__String_61b
{
    public  @Nullable String badSource() throws Throwable
    {
        String data;

        /* POTENTIAL FLAW: data is null */
        data = null;

        return data;
    }

    /* goodG2B() - use goodsource and badsink */
    public String goodG2BSource() throws Throwable
    {
        String data;

        /* FIX: hardcode data to non-null */
        data = "This is not null";

        return data;
    }

    /* goodB2G() - use badsource and goodsink */
    public  @Nullable String goodB2GSource() throws Throwable
    {
        String data;

        /* POTENTIAL FLAW: data is null */
        data = null;

        return data;
    }
}
