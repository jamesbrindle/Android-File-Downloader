package mobile.magpie.services;

import java.io.File;

import mobile.magpie.helpers.ActivityHelper;
import mobile.magpie.MainApplication;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;

/**
 * 
 * This class is responsible for dealing with information pushed to it by the
 * download service and decides what to do with the information. It is
 * responsible for updating the progress bar of the download dialog and
 * dismissing it.
 * 
 */
public class DownloadReceiver extends ResultReceiver
{
	private ProgressDialog dialogToUpdate;
	private AlertDialog.Builder completionDialog;
	private Context context;
	private String outputFilePath;

	private boolean completionDialogShown = false;

	public DownloadReceiver(String outputFileName, Context context, Handler handler, ProgressDialog dialogToUpdate,
			AlertDialog.Builder completionDialog)
	{
		super(handler);
		this.dialogToUpdate = dialogToUpdate;
		this.completionDialog = completionDialog;
		this.context = context;
		this.outputFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()
				+ "/" + outputFileName;
	}

	@Override
	protected void onReceiveResult(int resultCode, Bundle resultData)
	{
		super.onReceiveResult(resultCode, resultData);

		if (resultCode == DownloadService.UPDATE_PROGRESS)
		{
			int progress = resultData.getInt("progress");
			dialogToUpdate.setProgress(progress);

			if (progress == 100)
			{
				// Tell media scanner to add the file
				scanMedia(outputFilePath);
				dialogToUpdate.dismiss();

				if (!completionDialogShown
						&& (resultData.getString("error") == null || resultData.getString("error").isEmpty()))
				{
					completionDialog.show();
					completionDialogShown = true;
				}
			}
		}

		String errorMsg = "";

		try
		{
			errorMsg = resultData.getString("error");

			if (errorMsg != null)
			{
				if (!errorMsg.isEmpty())
				{
					ActivityHelper.ShowAlertDialog(context, "Download Error ",
							"An error occured while trying to download your selected file.");

					System.out.println(errorMsg);
				}
			}
		}
		catch (Exception e)
		{
			MainApplication
					.getInstance()
					.getLoggerService()
					.writeLog(true, "DownloadReceiver", "onReceiveResult",
							(e.getMessage() == null ? "Unknown error" : e.getMessage()));
		}
	}

	/**
	 * Class used in an attempt to interact with the android devices media
	 * scanner so that is shows up in the download manager app. It won't work on
	 * all devices however
	 */
	private void scanMedia(String path)
	{
		File file = new File(path);
		Uri uri = Uri.fromFile(file);
		Intent scanFileIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
		context.sendBroadcast(scanFileIntent);
	}
}