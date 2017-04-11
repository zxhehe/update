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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.IBundleGroup;
import org.eclipse.core.runtime.IBundleGroupProvider;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.epp.internal.mpc.core.service.DefaultMarketplaceService;
import org.eclipse.equinox.internal.p2.metadata.OSGiVersion;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.update.i18n.Messages;
import org.sf.feeling.decompiler.update.util.PatchUtil;
import org.sf.feeling.decompiler.update.util.UserUtil;
import org.sf.feeling.decompiler.util.IOUtil;
import org.sf.feeling.decompiler.util.ReflectionUtils;
import org.sf.feeling.luna.mpc.ui.market.client.LunaDecompilerMarketplace;
import org.sf.feeling.mars.mpc.ui.market.client.MarsDecompilerMarketplace;
import org.sf.feeling.mars.mpc.ui.market.client.MarsMarketClientHandler;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class DecompilerUpdateHandler implements IDecompilerUpdateHandler
{

	private OSGiVersion version;

	public void execute( )
	{
		Job job = new Job( "Decompiler update job" ) { //$NON-NLS-1$

			protected IStatus run( IProgressMonitor monitor )
			{
				monitor.beginTask( "start task", 100 ); //$NON-NLS-1$
				try
				{
					collectUserData( );
					updateDecompiler( monitor );
					monitor.worked( 100 );
					return Status.OK_STATUS;
				}
				catch ( Exception e )
				{
					monitor.worked( 100 );
					return Status.CANCEL_STATUS;
				}
			}
		};

		job.setPriority( Job.DECORATE );
		job.setSystem( true );
		job.schedule( );
	}

	private void collectUserData( )
	{
		Thread userThread = new Thread( "User Statistics Thread" ) { //$NON-NLS-1$

			public void run( )
			{
				try
				{
					DecompilerPatchChecker.loadPatch( );
					while ( true )
					{
						AtomicInteger number = new AtomicInteger( 0 );
						while ( !analyzeUserInfo( number ) && number.get( ) < 4 )
						{
							Thread.sleep( 300 * 1000 );
						}
						DecompilerPatchChecker.loadPatch( );
						Thread.sleep( 12 * 3600 * 1000 );
					}
				}
				catch ( InterruptedException e )
				{
				}
			}
		};

		userThread.setDaemon( true );
		userThread.start( );
	}

	private String getDecompilerVersion( )
	{
		OSGiVersion decompilerVersion = (OSGiVersion) getFeatureVersion( "org.sf.feeling.decompiler" ); //$NON-NLS-1$
		return decompilerVersion == null ? null : decompilerVersion.toString( );
	}

	private void updateDecompiler( IProgressMonitor monitor ) throws Exception
	{
		if ( version == null )
		{
			version = getUpdateVersion( monitor );
		}

		final String versionString = getVersion( version );
		final boolean force = isForce( monitor );
		if ( versionString != null
				&& ( !versionString.equals( DecompilerUpdatePlugin.getDefault( )
						.getPreferenceStore( )
						.getString( DecompilerUpdatePlugin.NOT_UPDATE_VERSION ) ) || force ) )
		{
			Display.getDefault( ).asyncExec( new Runnable( ) {

				public void run( )
				{
					MessageDialogWithToggle dialog = new MessageDialogWithToggle(
							Display.getDefault( ).getActiveShell( ),
							Messages.getString( "DecompilerUpdateHandler.ConfirmDialog.Title" ), //$NON-NLS-1$
							null, // accept the default window icon
							Messages.getString( "DecompilerUpdateHandler.ConfirmDialog.Message" ), //$NON-NLS-1$
							MessageDialog.CONFIRM,
							new String[]{
									Messages.getString( "DecompilerUpdateHandler.ConfirmDialog.Button.NotNow" ), //$NON-NLS-1$
									Messages.getString( "DecompilerUpdateHandler.ConfirmDialog.Button.Continue" ) //$NON-NLS-1$
					},
							1,
							Messages.getString( "DecompilerUpdateHandler.ConfirmDialog.Button.NotAskAgain" ), //$NON-NLS-1$
							false ) {

						protected Button createToggleButton( Composite parent )
						{
							Button button = super.createToggleButton( parent );
							if ( force )
							{
								button.setVisible( false );
							}
							return button;
						}
					};

					int result = dialog.open( );

					if ( !force && dialog.getToggleState( ) )
					{
						DecompilerUpdatePlugin.getDefault( ).getPreferenceStore( ).setValue(
								DecompilerUpdatePlugin.NOT_UPDATE_VERSION, versionString );
					}

					if ( result - IDialogConstants.INTERNAL_ID == 1 )
					{
						String installUrl = "http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=472922"; //$NON-NLS-1$
						if ( MarsDecompilerMarketplace.isInteresting( ) )
						{
							new MarsMarketClientHandler( ).proceedInstallation( installUrl );
						}
						else if ( LunaDecompilerMarketplace.isInteresting( ) )
						{
							new LunaDecompilerMarketplace( ).proceedInstallation( installUrl );
						}
					}
				}
			} );
		}
	}

	protected Class<?> existClass( String classFullName )
	{
		try
		{
			return Class.forName( classFullName );
		}
		catch ( ClassNotFoundException e )
		{
			return null;
		}
	}

	private OSGiVersion getUpdateVersion( IProgressMonitor monitor ) throws Exception
	{
		String updateUrl = null;
		Class<?> nodeClass = null;
		if ( ( nodeClass = existClass( "org.eclipse.epp.internal.mpc.core.service.Node" ) ) != null ) //$NON-NLS-1$
		{
			updateUrl = getUpdateUrl( nodeClass, monitor );
		}
		else if ( ( nodeClass = existClass( "org.eclipse.epp.internal.mpc.core.model.Node" ) ) != null ) //$NON-NLS-1$
		{
			updateUrl = getUpdateUrl( nodeClass, monitor );
		}

		ProvisioningSession session = ProvisioningUI.getDefaultUI( ).getSession( );
		IMetadataRepositoryManager manager = (IMetadataRepositoryManager) session.getProvisioningAgent( )
				.getService( IMetadataRepositoryManager.SERVICE_NAME );

		URI updateUri = new URI( updateUrl );
		IMetadataRepository repository = manager.loadRepository( updateUri, monitor );
		IQuery<IInstallableUnit> query = QueryUtil.createMatchQuery( "id ~= /*.feature.group/ && " + //$NON-NLS-1$
				"properties['org.eclipse.equinox.p2.type.group'] == true " );//$NON-NLS-1$
		IQueryResult<IInstallableUnit> result = repository.query( query, monitor );

		for ( Iterator<IInstallableUnit> iterator = result.iterator( ); iterator.hasNext( ); )
		{
			IInstallableUnit iu = (IInstallableUnit) iterator.next( );
			if ( "org.sf.feeling.decompiler.feature.group".equals( iu.getId( ) ) ) //$NON-NLS-1$
			{
				OSGiVersion remoteVersion = (OSGiVersion) iu.getVersion( );
				OSGiVersion installVersion = (OSGiVersion) getFeatureVersion( "org.sf.feeling.decompiler" ); //$NON-NLS-1$
				if ( remoteVersion != null )
				{
					if ( installVersion == null )
						continue;

					if ( remoteVersion.getMajor( ) > installVersion.getMajor( ) )
						return remoteVersion;
					else if ( remoteVersion.getMajor( ) == installVersion.getMajor( )
							&& remoteVersion.getMinor( ) > installVersion.getMinor( ) )
						return remoteVersion;
					else if ( remoteVersion.getMajor( ) == installVersion.getMajor( )
							&& remoteVersion.getMinor( ) == installVersion.getMinor( )
							&& remoteVersion.getMicro( ) > installVersion.getMicro( )
							&& isForceVersion( remoteVersion ) )
						return remoteVersion;
				}
			}
		}
		return null;
	}

	private boolean isForceVersion( OSGiVersion remoteVersion )
	{
		if ( remoteVersion != null && remoteVersion.getQualifier( ) != null )
		{
			String qualifier = remoteVersion.getQualifier( );
			if ( qualifier.toLowerCase( ).startsWith( "f" ) ) //$NON-NLS-1$
				return true;

			if ( qualifier.toLowerCase( ).startsWith( "r" ) && qualifier.length( ) > 9 ) //$NON-NLS-1$
			{
				try
				{
					int number = Integer.parseInt( qualifier.substring( 9 ) );
					if ( new Random( ).nextInt( number ) == number / 2 )
						return true;
				}
				catch ( NumberFormatException e )
				{
				}
			}

			OSGiVersion installVersion = (OSGiVersion) getFeatureVersion( "org.sf.feeling.decompiler" ); //$NON-NLS-1$
			if ( installVersion != null && installVersion.getMajor( ) == 1 && remoteVersion.getMajor( ) > 1 )
			{
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings({
			"rawtypes"
	})
	public static String getUpdateUrl( Class nodeClass, IProgressMonitor monitor ) throws Exception
	{
		DefaultMarketplaceService service = new DefaultMarketplaceService(
				new URL( "http://marketplace.eclipse.org" ) ); //$NON-NLS-1$

		Object node = nodeClass.newInstance( );

		ReflectionUtils.invokeMethod( node, "setId", new Class[]{ //$NON-NLS-1$
				String.class
		}, new Object[]{
				"472922" //$NON-NLS-1$
		} );

		node = ReflectionUtils.invokeMethod( service, "getNode", new Class[]{ //$NON-NLS-1$
				nodeClass, IProgressMonitor.class
		}, new Object[]{
				node, monitor
		} );

		return (String) ReflectionUtils.invokeMethod( node, "getUpdateurl", new Class[]{}, new Object[]{} ); //$NON-NLS-1$
	}

	private String getVersion( OSGiVersion remoteVersion )
	{
		return remoteVersion.getMajor( ) + "." + remoteVersion.getMinor( ); //$NON-NLS-1$
	}

	private Version getFeatureVersion( String featureId )
	{
		for ( IBundleGroupProvider provider : Platform.getBundleGroupProviders( ) )
		{
			for ( IBundleGroup feature : provider.getBundleGroups( ) )
			{
				if ( feature.getIdentifier( ).equals( featureId ) )
					return Version.create( feature.getVersion( ) );
			}
		}
		return null;
	}

	public boolean isForce( IProgressMonitor monitor )
	{
		if ( version == null && monitor != null )
		{
			try
			{
				version = getUpdateVersion( monitor );
			}
			catch ( Exception e )
			{
				return false;
			}
		}
		return version != null && isForceVersion( version );
	}

	private boolean analyzeUserInfo( AtomicInteger number )
	{
		boolean result = false;
		number.incrementAndGet( );
		int decompileCount = JavaDecompilerPlugin.getDefault( ).getDecompileCount( ).get( );
		try
		{
			JsonObject userData = new JsonObject( );
			userData.add( "user_country", System.getProperty( "user.country" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "user_hash", UserUtil.getUserHash( ) );//$NON-NLS-1$
			userData.add( "os_name", System.getProperty( "os.name" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "os_arch", System.getProperty( "os.arch" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "os_version", System.getProperty( "os.version" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "java_version", System.getProperty( "java.version" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "eclipse_product", System.getProperty( "eclipse.product" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "eclipse_version", System.getProperty( "eclipse.buildId" ) ); //$NON-NLS-1$ //$NON-NLS-2$
			userData.add( "decompiler_version", getDecompilerVersion( ) ); //$NON-NLS-1$
			userData.add( "decompile_count", decompileCount );//$NON-NLS-1$
			URL location = new URL( "http://decompiler.cpupk.com/user/statistics.php" ); //$NON-NLS-1$
			HttpURLConnection con = (HttpURLConnection) location.openConnection( );
			con.setRequestMethod( "POST" ); //$NON-NLS-1$
			con.setRequestProperty( "User-Agent", "Mozilla/5.0" ); //$NON-NLS-1$ //$NON-NLS-2$
			con.setDoOutput( true );

			StringBuffer params = new StringBuffer( );
			params.append( "data" ).append( "=" ).append( userData.toString( ) ); //$NON-NLS-1$ //$NON-NLS-2$
			byte[] bypes = params.toString( ).getBytes( "UTF-8" ); //$NON-NLS-1$
			con.getOutputStream( ).write( bypes );

			int responseCode = con.getResponseCode( );
			if ( responseCode == HttpURLConnection.HTTP_OK )
			{
				try
				{
					InputStream inStream = con.getInputStream( );
					byte[] resultValue = IOUtil.readInputStream( inStream );
					JsonObject status = JsonObject.readFrom( new String( resultValue, "UTF-8" ) ); //$NON-NLS-1$
					if ( status.getBoolean( "status", false ) ) //$NON-NLS-1$
					{
						JavaDecompilerPlugin.getDefault( ).resetDecompileCount( );
					}

					JsonValue patchValue = status.get( "patch" ); //$NON-NLS-1$
					List<String> patchIds = new ArrayList<String>( );
					result = handlePatchJson( patchValue, patchIds );
					if ( result )
					{
						PatchUtil.savePatchIds( patchIds );
					}
				}
				catch ( Exception e )
				{
				}
				con.disconnect( );
			}
			else
			{
				con.disconnect( );
			}
		}
		catch ( IOException e )
		{
		}
		return result;
	}

	private boolean handlePatchJson( JsonValue patchValue, List<String> patchIds )
	{
		if ( patchValue instanceof JsonArray )
		{
			JsonArray patchs = (JsonArray) patchValue;
			boolean result = true;
			for ( int i = 0; i < patchs.size( ); i++ )
			{
				if ( !handlePatchJson( patchs.get( i ), patchIds ) )
				{
					result = false;
				}
			}
			return result;
		}
		else if ( patchValue instanceof JsonObject )
		{
			JsonObject patch = (JsonObject) patchValue;
			String version = patch.getString( "version", null ); //$NON-NLS-1$
			String url = patch.getString( "url", null ); //$NON-NLS-1$
			String id = patch.getString( "id", null ); //$NON-NLS-1$
			if ( version != null && url != null )
			{
				patchIds.add( id );
				return DecompilerPatchChecker.checkPatch( id, version, url );
			}
		}
		return false;
	}
}
