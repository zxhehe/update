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

package org.sf.feeling.decompiler.update;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.core.runtime.IPath;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.swt.widgets.Display;
import org.sf.feeling.decompiler.update.util.PatchUtil;
import org.sf.feeling.decompiler.util.FileUtil;

public class DecompilerPatchChecker
{

	public static boolean checkPatch( String patchId, String version, String downloadUrl )
	{
		try
		{
			IPath path = DecompilerUpdatePlugin.getDefault( ).getStateLocation( );
			IPath patchDir = path.append( "patch" ); //$NON-NLS-1$
			File patchFolder = patchDir.toFile( );
			if ( !patchFolder.exists( ) )
			{
				patchFolder.mkdirs( );
				return downloadPatch( patchFolder, patchId, version, downloadUrl );
			}
			else
			{
				Version localVerion = PatchUtil.getLocalPatchVersion( patchFolder, patchId );
				Version remoteVersion = Version.parseVersion( version );
				if ( remoteVersion != null )
				{
					if ( localVerion == null || remoteVersion.compareTo( localVerion ) > 0 )
					{
						return downloadPatch( patchFolder, patchId, version, downloadUrl );
					}
					else
						return true;
				}
				else
					return true;
			}
		}
		catch ( Throwable e )
		{
			return false;
		}
	}

	private static boolean downloadPatch( File patchFolder, String patchId, String version, String downloadUrl )
	{
		try
		{
			URL location = new URL( downloadUrl ); // $NON-NLS-1$
			HttpURLConnection con = (HttpURLConnection) location.openConnection( );
			con.setRequestMethod( "GET" ); //$NON-NLS-1$
			con.setRequestProperty( "User-Agent", "Mozilla/5.0" ); // $NON-NLS-1$ //$NON-NLS-1$//$NON-NLS-2$

			int responseCode = con.getResponseCode( );
			if ( responseCode == HttpURLConnection.HTTP_OK )
			{
				if ( patchId == null )
				{
					patchId = PatchUtil.DEFAULT_PATCH_PLUGIN_ID;
				}
				FileUtil.writeToBinarayFile( new File( patchFolder, patchId + "_" + version + ".jar" ), //$NON-NLS-1$ //$NON-NLS-2$
						con.getInputStream( ),
						true );
				con.disconnect( );
				return true;
			}
			else
			{
				con.disconnect( );
				return false;
			}
		}
		catch ( Exception e )
		{
			return false;
		}
	}

	public static boolean loadPatch( )
	{
		final boolean[] result = new boolean[]{
				true
		};

		Display.getDefault( ).syncExec( new Runnable( ) {

			public void run( )
			{
				IPath path = DecompilerUpdatePlugin.getDefault( ).getStateLocation( );
				IPath patchDir = path.append( "patch" ); //$NON-NLS-1$
				File patchFolder = patchDir.toFile( );
				if ( !patchFolder.exists( ) )
					patchFolder.mkdirs( );
				String[] patchIds = PatchUtil.loadPatchIds( );
				if ( patchIds != null )
				{
					for ( int i = 0; i < patchIds.length; i++ )
					{
						String patchId = patchIds[i].trim( );
						File patchFile = PatchUtil.getLatestPatch( patchFolder, patchId );
						if ( patchFile != null && patchFile.exists( ) && patchFile.isFile( ) )
						{
							if ( !PatchUtil.installPatch( patchFile ) )
								result[0] = false;
						}
					}
				}
			}
		} );

		return result[0];
	}

}
