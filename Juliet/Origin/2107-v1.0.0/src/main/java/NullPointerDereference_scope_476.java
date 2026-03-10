/* 
 * This software was developed at the National Institute of Standards and
 * Technology by employees of the Federal Government in the course of their
 * official duties. Pursuant to title 17 Section 105 of the United States
 * Code this software is not subject to copyright protection and is in the
 * public domain. NIST assumes no responsibility whatsoever for its use by
 * other parties, and makes no guarantees, expressed or implied, about its
 * quality, reliability, or any other characteristic.
 *
 * This reference program was developed in June 2009 as part of the Software
 * Assurance Metrics And Tool Evaluation (SAMATE) project.
 * We would appreciate acknowledgment if the software is used.
 * The SAMATE project website is: http://samate.nist.gov
 */

/*
 * This code has a NULL Pointer Dereference CWE-476 vulnerability with a scope
 * complexity. http://cwe.mitre.org
 * Here we try to get the system property from a specified key and
 * then remove whitespace. If the key is unknown, calling cmd gives
 * a NULL dereference exception.
 */

import java.util.logging.Logger;

public class NullPointerDereference_scope_476
{
	public String function( String cmd )
	{
		// BUG
		cmd = cmd.trim();
		// Returns the string, with leading and trailing whitespace omitted

		// If java.class.path is undefined, then return value of
		// systemProperty is undefined. So cmd is not defined.
		// Thus, calling cmd.trim() causes a NULL dereference exception.
		return cmd;
	}
	
	public NullPointerDereference_scope_476()
	{
		try
		{
			// Gets the system property indicated by the specified key
			String cmd = System.getProperty( "java.class.path" );
			
			// BUG
			cmd = function( cmd );
		}
		catch ( RuntimeException re )
		{
			final Logger logger = Logger.getAnonymousLogger();
			String exception = "Exception " + re;
			logger.warning( exception );
		}
	}
	
	public static void main( String[] argv )
	{
		new NullPointerDereference_scope_476();
	}
}

// end of NullPointerDereference_scope_476.java

