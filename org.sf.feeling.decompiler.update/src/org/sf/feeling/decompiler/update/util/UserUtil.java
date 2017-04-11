/*******************************************************************************
 * Copyright (c) 2016 Chen Chao(cnfree2000@hotmail.com).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Chen Chao  - initial API and implementation
 *******************************************************************************/

package org.sf.feeling.decompiler.update.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.sf.feeling.decompiler.util.ReflectionUtils;

public class UserUtil
{

	public static String getUserHash( )
	{
		try
		{
			Enumeration<NetworkInterface> ni = NetworkInterface.getNetworkInterfaces( );
			while ( ni.hasMoreElements( ) )
			{
				NetworkInterface netI = ni.nextElement( );
				byte[] bytes = (byte[]) ReflectionUtils.invokeMethod( netI,
						"getHardwareAddress", //$NON-NLS-1$
						new Class[0],
						new Object[0] );
				Boolean isUp = (Boolean) ReflectionUtils.invokeMethod( netI, "isUp", new Class[0], new Object[0] ); //$NON-NLS-1$
				if ( Boolean.TRUE.equals( isUp ) && netI != null && bytes != null && bytes.length == 6 )
				{
					StringBuffer sb = new StringBuffer( );
					for ( byte b : bytes )
					{
						sb.append( Integer.toHexString( ( b & 240 ) >> 4 ) );
						sb.append( Integer.toHexString( b & 15 ) );
						sb.append( "-" ); //$NON-NLS-1$
					}
					sb.deleteCharAt( sb.length( ) - 1 );
					return String.valueOf( sb.toString( ).toUpperCase( ).hashCode( ) );
				}
			}
			return String.valueOf( InetAddress.getLocalHost( ).getHostName( ).hashCode( ) );
		}
		catch ( Exception e )
		{
		}
		return null;
	}

	public static void main( String[] args )
	{
		for ( int i = 0; i < 100; i++ )
		{
			System.out.println( UserUtil.getUserHash( ) );

		}
	}
}
