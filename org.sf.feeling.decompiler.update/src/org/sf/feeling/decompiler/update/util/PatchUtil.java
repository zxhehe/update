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

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.equinox.p2.metadata.Version;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.sf.feeling.decompiler.update.DecompilerUpdatePlugin;

public class PatchUtil
{

	public static final String DEFAULT_PATCH_PLUGIN_ID = "org.sf.feeling.decompiler.patch"; //$NON-NLS-1$

	public static File getLatestPatch( File patchFolder, final String patchId )
	{
		if ( patchFolder != null )
		{
			File[] children = patchFolder.listFiles( new FileFilter( ) {

				public boolean accept( File file )
				{
					if ( file.isDirectory( ) )
						return false;
					if ( getPatchFileVersion( file, patchId ) != null )
						return true;
					return false;
				}
			} );

			if ( children != null && children.length > 0 )
			{
				if ( children.length > 1 )
				{
					Arrays.sort( children, new Comparator<File>( ) {

						public int compare( File o1, File o2 )
						{
							Version v1 = getPatchFileVersion( o1, patchId );
							Version v2 = getPatchFileVersion( o2, patchId );
							return v2.compareTo( v1 );
						}
					} );
				}
				return children[0];
			}
		}
		return null;
	}

	public static Version getLocalPatchVersion( File patchFolder, final String patchId )
	{
		if ( patchFolder != null )
		{
			File[] children = patchFolder.listFiles( new FileFilter( ) {

				public boolean accept( File file )
				{
					if ( file.isDirectory( ) )
						return false;
					if ( getPatchFileVersion( file, patchId ) != null )
						return true;
					return false;
				}
			} );

			if ( children != null && children.length > 0 )
			{
				if ( children.length > 1 )
				{
					Arrays.sort( children, new Comparator<File>( ) {

						public int compare( File o1, File o2 )
						{
							Version v1 = getPatchFileVersion( o1, patchId );
							Version v2 = getPatchFileVersion( o2, patchId );
							return v2.compareTo( v1 );
						}
					} );
				}
				return getPatchFileVersion( children[0], patchId );
			}
		}
		return null;
	}

	private static Version getPatchFileVersion( File file, String patchId )
	{
		if ( patchId == null )
		{
			patchId = DEFAULT_PATCH_PLUGIN_ID;
		}
		return Version.parseVersion( file.getName( )
				.toLowerCase( )
				.replace( patchId + "_", "" ) //$NON-NLS-1$ //$NON-NLS-2$
				.replace( ".jar", "" ) ); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static boolean installPatch( File file )
	{
		BundleContext context = FrameworkUtil.getBundle( DecompilerUpdatePlugin.class ).getBundleContext( );
		if ( DecompilerUpdatePlugin.getDefault( ).getPatchFile( ) != null )
		{
			if ( DecompilerUpdatePlugin.getDefault( ).getPatchFile( ).equals( file ) )
				return true;
			try
			{
				Bundle bundle = context
						.getBundle( DecompilerUpdatePlugin.getDefault( ).getPatchFile( ).toURI( ).toString( ) );
				bundle.uninstall( );
			}
			catch ( BundleException e )
			{
				e.printStackTrace( );
			}
		}
		try
		{
			Bundle bundle = context.installBundle( file.toURI( ).toString( ) );
			if ( bundle != null )
			{
				bundle.start( );
				DecompilerUpdatePlugin.getDefault( ).setPatchFile( file );
				return true;
			}
		}
		catch ( BundleException e )
		{
			e.printStackTrace( );
		}
		return false;
	}

	public static String[] loadPatchIds( )
	{
		String patchIds = DecompilerUpdatePlugin.getDefault( ).getPreferenceStore( ).getString( "patchIds" ); //$NON-NLS-1$
		if ( patchIds != null )
		{
			return patchIds.split( "," ); //$NON-NLS-1$
		}
		return new String[0];
	}

	public static void savePatchIds( List<String> patchIds )
	{
		if ( patchIds != null && patchIds.size( ) > 0 )
		{
			DecompilerUpdatePlugin.getDefault( ).getPreferenceStore( ).setValue( "patchIds", //$NON-NLS-1$
					Arrays.toString( patchIds.toArray( new String[0] ) )
							.replace( "[", "" ) //$NON-NLS-1$ //$NON-NLS-2$
							.replace( "]", "" ) //$NON-NLS-1$ //$NON-NLS-2$
							.trim( ) );
		}
	}

}
