package mobile.magpie.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import mobile.magpie.MainApplication;
import mobile.magpie.helpers.ActivityHelper;

import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ResultReceiver;
import android.util.Base64;

/**
 * 
 * This class is used to download documents and media to the android device from
 * the Powershare client.
 * 
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class DownloadService extends IntentService
{
	public static final int UPDATE_PROGRESS = 8344;
	private String authString = "";

	public DownloadService()
	{
		super("DownloadService");
	}

	/**
	 * One means in which an activity to start a download is by starting a
	 * download intent
	 */
	@Override
	protected void onHandleIntent(Intent intent)
	{
		String urlToDownload = intent.getStringExtra("url");
		String fileName = intent.getStringExtra("fileName");
		boolean overwriteFile = intent.getBooleanExtra("overwrite", true);

		String errorMsg = "";

		ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");

		try
		{
			URL url = new URL(urlToDownload);
			URLConnection connection = null;

			if (url.getProtocol().equalsIgnoreCase("https"))
			{
				MagpieSSLSocketFactory.trustAllHosts();
				connection = (HttpsURLConnection) url.openConnection();
				((HttpsURLConnection) connection).setHostnameVerifier(MagpieSSLSocketFactory.DO_NOT_VERIFY);
			}
			else
			{
				connection = (HttpURLConnection) url.openConnection();
			}

			String username = MainApplication.getInstance().getAuthUsername();
			String password = MainApplication.getInstance().getAuthPassword();
			authString = Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP);

			MagpieSSLSocketFactory.trustAllHosts();

			connection.addRequestProperty("Authorization", "Basic " + authString);
			connection.setRequestProperty("Authorization", "Basic " + authString);

			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ");
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
			connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
			connection.setReadTimeout(15 * 1000);

			connection.connect();

			// this will be useful so that you can show a typical 0-100%
			// progress bar
			int fileLength = connection.getContentLength();

			// download the file
			InputStream input = new BufferedInputStream(url.openStream());

			// If the download folder doesn't exist create it, also don't
			// overwrite existing files, so append "(1)" etc to the current
			// filename (unless the user requested otherwise)

			if (!overwriteFile)
				fileName = createPossibleFolderAndGetFilename(fileName);

			OutputStream output = new FileOutputStream(Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_DOWNLOADS).getPath()
					+ "/" + fileName);

			byte data[] = new byte[1024];
			long total = 0;
			int count;

			while ((count = input.read(data)) != -1)
			{
				total += count;
				// publishing the progress....
				Bundle resultData = new Bundle();
				resultData.putInt("progress", (int) (total * 100 / fileLength));
				receiver.send(UPDATE_PROGRESS, resultData);
				output.write(data, 0, count);
			}

			output.flush();
			output.close();
			input.close();
		}
		catch (IOException e)
		{
			errorMsg = "Error: " + (e.getMessage() == null ? "Unknown error" : e.getMessage()) + ". StackTrace: "
					+ e.getStackTrace().toString();
		}

		// finish off download receiver, regardless of outcome - it will still
		// show an error if there's a problem in the calling class
		Bundle resultData = new Bundle();
		resultData.putInt("progress", 100);
		resultData.putString("error", errorMsg);

		receiver.send(UPDATE_PROGRESS, resultData);
	}

	/**
	 * This class is responsible for creating the download folder on the android
	 * device if it doesn't exist already
	 * 
	 * @param fileName
	 *            The name of the file, containing the whole path of which to
	 *            check and create
	 * @return The filename (including path) itself after the path to the file
	 *         has been created
	 */
	private String createPossibleFolderAndGetFilename(String fileName)
	{
		// Check the downloads folder exists, if it doesn't then create it
		File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());

		if (!folder.exists())
			folder.mkdir();

		File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()
				+ "/" + fileName);

		String newFileName = fileName;
		int newFileLooper = 1;

		while (file.exists())
		{
			String prefix = fileName.substring(ActivityHelper.getFilePrefixStartIndex(fileName));
			newFileName = fileName.replace(prefix, "") + "(" + newFileLooper + ")" + prefix;

			file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()
					+ "/" + newFileName);

			newFileLooper++;
		}

		return newFileName;
	}

	/**
	 * In some of the more older android OS versions the download manager won't
	 * exist. This method checks to see if it available and used to ultimately
	 * determine which download manager will be used. Powershare's internal one
	 * or the android download manager
	 * 
	 * @param context
	 *            The context of the calling activity
	 * @return True if the download manager is available to the current OS.
	 *         False otherwise
	 */
	public static boolean isDownloadManagerAvailable()
	{
		try
		{
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
			{
				return false;
			}

			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			intent.setClassName("com.android.providers.downloads.ui", "com.android.providers.downloads.ui.DownloadList");
			List<ResolveInfo> list = MainApplication.getInstance().getPackageManager()
					.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			return list.size() > 0;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	/**
	 * This method will download the specified file via android's download
	 * manager.
	 * 
	 * @param context
	 *            The context of the calling activity.
	 * @param url
	 *            The URL to the file to download
	 * @param fileName
	 *            The filename that the file will have once it has been
	 *            downloaded
	 * @param title
	 *            The title to display in the notification area for the download
	 * @param description
	 *            The description to display in the notification area for the
	 *            download
	 */
	public static void downloadWithDownloadManager(Context context, String url, String fileName, String title,
			String description)
	{
		DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

		String username = MainApplication.getInstance().getAuthUsername();
		String password = MainApplication.getInstance().getAuthPassword();

		String authString = Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP);

		request.addRequestHeader("Authorization", "Basic " + authString);
		request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

		request.setAllowedOverRoaming(true);
		request.setTitle(title);
		request.setDescription(description);

		// in order for this if to run, you must use the android 3.2 to compile
		// your app
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			request.allowScanningByMediaScanner();
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
		}

		request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

		// get download service and enqueue file
		DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		manager.enqueue(request);

		long downloadReference = manager.enqueue(request);

		Query downloadQuery = new Query();

		// set the query filter to our previously Enqueued download
		downloadQuery.setFilterById(downloadReference);

		// wait 2 seconds then check if the download has failed
		try
		{
			Thread.sleep(2000);
		}
		catch (InterruptedException e)
		{
			MainApplication
					.getInstance()
					.getLoggerService()
					.writeLog(
							true,
							"DownloadService",
							"downloadWithDownloadManager",
							"Error while trying to sleep a thread: "
									+ (e.getMessage() == null ? "Unknown error" : e.getMessage()));
		}

		try
		{
			// Query the download manager about downloads that have been
			// requested.
			Cursor cursor = manager.query(downloadQuery);

			// Known bug of the download manager requesting the download twice,
			// in which case remove the first request
			// as this is the one we have an ID for:
			// http://code.google.com/p/android/issues/detail?id=18462
			manager.remove(downloadReference);

			if (cursor.moveToFirst())
			{
				// Query the download manager about downloads that have been
				// requested.
				String downloadFailedMsg = checkDownloadFailure(cursor);

				if (!downloadFailedMsg.isEmpty())
				{
					new ActivityHelper();
					ActivityHelper.ShowAlertDialog(context, "Download Error", downloadFailedMsg);
				}
			}
		}
		catch (Exception e)
		{
			MainApplication
					.getInstance()
					.getLoggerService()
					.writeLog(true, "DownloadService", "downloadWithDownloadManager",
							"Download query error: " + (e.getMessage() == null ? "Unknown error" : e.getMessage()));
		}
	}

	/**
	 * This method is used to check the status of the download, which is found
	 * by probing the cursor for the download and fetching back relevant
	 * information, which is used to determine if the download has ultimately
	 * failed, and the reason for the failure.
	 * 
	 * @param cursor
	 *            This interface provides random read-write access to the result
	 *            set returned by a database query.
	 * @return The reason for failure if there is any, and an empty string is
	 *         there isn't.
	 */
	private static String checkDownloadFailure(Cursor cursor)
	{
		// column for status
		int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
		int status = cursor.getInt(columnIndex);
		// column for reason code if the download failed or paused
		int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
		int reason = cursor.getInt(columnReason);

		String statusText = "";
		String reasonText = "";

		switch (status)
		{
			case DownloadManager.STATUS_FAILED:
				statusText = "STATUS_FAILED";
				switch (reason)
				{
					case DownloadManager.ERROR_CANNOT_RESUME:
						reasonText = "Couldn't resume download";
						break;
					case DownloadManager.ERROR_DEVICE_NOT_FOUND:
						reasonText = "failed to find mobile device";
						break;
					case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
						reasonText = "";
						break;
					case DownloadManager.ERROR_FILE_ERROR:
						reasonText = "Couldn't read the selected file";
						break;
					case DownloadManager.ERROR_HTTP_DATA_ERROR:
						reasonText = "An error occurred in the http requred";
						break;
					case DownloadManager.ERROR_INSUFFICIENT_SPACE:
						reasonText = "Insufficient storage space";
						break;
					case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
						reasonText = "Too many redirects in the http request";
						break;
					case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
						reasonText = "Unknown problem with http request";
						break;
					case DownloadManager.ERROR_UNKNOWN:
						reasonText = "Unknown";
						break;
					default:
						reasonText = "Unknown";
						break;
				}
				break;
		}

		if (statusText.isEmpty())
			return "";
		else
		{
			MainApplication
					.getInstance()
					.getLoggerService()
					.writeLog(false, "DownloadService", "CheckDownloadFailure",
							"Download Status: " + statusText + ", Reason: " + reasonText);

			return "Failied to download the selected file. Reason: " + reasonText + ".";
		}
	}
}