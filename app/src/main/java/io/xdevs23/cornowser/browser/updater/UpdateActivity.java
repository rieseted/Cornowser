package io.xdevs23.cornowser.browser.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import com.rey.material.widget.ProgressView;

import org.xdevs23.config.AppConfig;
import org.xdevs23.debugutils.Logging;
import org.xdevs23.net.DownloadUtils;
import org.xdevs23.root.utils.RootController;
import org.xdevs23.ui.dialog.MessageDialog;
import org.xdevs23.ui.dialog.templates.NegativeButtonCancel;
import org.xdevs23.ui.utils.BarColors;

import java.io.File;

import io.xdevs23.cornowser.browser.R;

@SuppressWarnings("unused")
public class UpdateActivity extends AppCompatActivity {
	
	private static String appversion = AppConfig.versionName;

	private Activity thisActivity = this;
	private boolean mgo = false;
	
	
	private static Context staticContext = null;
	private        Context     myContext = this;
	
	private static boolean enableRoot = false;
	
	private static       String updateRoot = AppConfig.updateRoot;
	private static 		 String updaterDir = updateRoot;
	
	private static       String readyToInstallUrl = "";
	
	private static       String updatedApk;

    private static       int    latestVersionCode = 0;

    private static       String latestVersionName = "";
	
	public static ProgressView updateBar;
	public static TextView     updateStatus;

    private WebView myWebView;
    private static boolean isDownloadingUpdate = false;

    private boolean webloaded = false;

    private static UpdateType updateType;


    private static void setStaticContext(Context context) {
        staticContext = context;
    }

    public static void startUpdateImmediately(Activity activity, String url) {
        activity.startActivity(new Intent(activity.getApplicationContext(), UpdateActivity.class));
        readyToInstallUrl = url;
    }

    protected static void logt(String msg) {
        Logging.logd("[Updater] " + msg);
    }

    private static class UpdateStatus {
		
		public
		  static String
		    downloading = "Downloading"			    ,
		        loading = "Loading"			        ,
		      launching = "Starting update"			,
		           none = ""						;
		    
		public static void setStatus(String status) {
			updateStatus.setText(status);
		}

        public static void applyLanguages() {
            downloading = staticContext.getString(R.string.updater_downloading);
            loading     = staticContext.getString(R.string.updater_loading);
            launching   = staticContext.getString(R.string.updater_updating);
        }
	}

    public enum UpdateType {
        RELEASE,
        BETA,
        ALPHA,
        NIGHTLY,
        NONE
    }


    private static void startNRUpdateInstallation() {
		File newUpdate   = new File(updatedApk);
		
		File newUpdDir   = new File(updatedApk.replace("ubp-update.apk", ""));
		
		boolean successCRND = newUpdDir.mkdirs();

		if(!successCRND) logt("Error while creating dirs");
		
    	Intent installSpIntent = new Intent(Intent.ACTION_VIEW)
  	                  .setDataAndType(Uri.fromFile(newUpdate), 
  	                		  "application/vnd.android.package-archive");
    	
    	installSpIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	
		staticContext.startActivity(installSpIntent);
	}



	public static void startOverallInstallation(String url) {
		File spexfile = new File(updatedApk);

        if(!spexfile.delete()) logt("File not deleted.");

		
		UpdateStatus.setStatus(UpdateStatus.downloading);
		
		
	    DownloadUtils.setProgressBar(R.id.updateProgressBar, staticContext);
		
		updateBar.applyStyle(R.style.ProgressView_MainStyle_Horiz_Determinate);
		updateBar.setVisibility(View.VISIBLE);
        updateBar.start();
	    
	    DownloadUtils.downloadFile(url, updatedApk);
	   
	    
	    isDownloadingUpdate = true;
	}
	
	public static void startUpdateInstallation() {
		
		UpdateStatus.setStatus(UpdateStatus.launching);
		
		try {
            String endR = "";
			if(enableRoot) endR = RootController.runCommand(
                    "am force-stop io.github.UltimateBrowserProject && " +
                            "pm install -r " + updatedApk + " && " +
                            "am start -n io.github.UltimateBrowserProject/.Activity.BrowserActivity && " +
                            "am start -n io.github.UltimateBrowserProject/.Activity.UpdateActivity && exit");
			else startNRUpdateInstallation();

            if(endR.length() > 0 && endR.toLowerCase().contains("failed")) {
                Toast.makeText(staticContext,
                        staticContext.getString(R.string.updater_root_install_error),
                        Toast.LENGTH_LONG)
                        .show();
                startNRUpdateInstallation();
            }
		} catch(Exception ex) {
			startNRUpdateInstallation();
		}
	
		UpdateStatus.setStatus(UpdateStatus.none);

		
	}
	
	public static void updateProgress(int progress) {
		updateBar.setProgress((float)progress / 100);
        updateBar.refreshDrawableState();
	}
	
	public static void endProgress() {
    	updateBar.applyStyle(R.style.ProgressView_MainStyle_Horiz);
    	updateBar.setProgress(0);
        updateBar.stop();
	}

    @SuppressLint("SetJavaScriptEnabled")
    protected void initThisActivity() {
        if(!webloaded) {
            updatedApk = (getApplicationContext().getExternalCacheDir() + "/CBUpdate.apk")
                    .replace("//", "/");

            updateBar = (ProgressView) findViewById(R.id.updateProgressBar);
            updateBar.setVisibility(View.VISIBLE);

            updateStatus = (TextView) findViewById(R.id.updateStatus);

            TextView currentVersionTv = (TextView) findViewById(R.id.updaterAppVersion);
            TextView newVersionTv     = (TextView) findViewById(R.id.updaterNewAppVersion);
            TextView changelogTitle   = (TextView) findViewById(R.id.updaterChangelogTitle);
            TextView changelogTv      = (TextView) findViewById(R.id.updaterChangelog);
            com.rey.material.widget.Button updaterButton =
                    (com.rey.material.widget.Button) findViewById(R.id.updaterUpdateAppButton);

            updaterButton.setVisibility(View.INVISIBLE);

            try {
                currentVersionTv.setText(String.format(getString(R.string.updater_prefix_actual_version),
                        getPackageManager().getPackageInfo(getPackageName(), 0).versionName));


                latestVersionCode = Integer.parseInt(
                        DownloadUtils.downloadString(UpdaterStorage.URL_VERSION_CODE)
                );

                switch( Integer.parseInt(
                        String.valueOf(latestVersionCode)
                                .substring(8, 9)
                ) ) {
                    case 0:case 1:case 2: updateType = UpdateType.RELEASE; break;
                    case 3:case 4:case 5: updateType = UpdateType.BETA   ; break;
                    case 6:case 7:        updateType = UpdateType.ALPHA  ; break;
                    case 8:               updateType = UpdateType.NIGHTLY; break;
                    case 9:               updateType = UpdateType.RELEASE; break;
                    default:              updateType = UpdateType.NONE   ; break;
                }

                latestVersionName = DownloadUtils.downloadString(UpdaterStorage.URL_VERSION_NAME);

                if(latestVersionCode <= getPackageManager().getPackageInfo(getPackageName(), 0)
                    .versionCode) updateType = UpdateType.NONE;

                newVersionTv.setText(String.format(getString(R.string.updater_prefix_latest_version),
                        latestVersionName
                ) + "\n" + getString(R.string.updater_msg_latest_installed));

                if(updateType != UpdateType.NONE) {
                    updaterButton.setVisibility(View.VISIBLE);
                    newVersionTv.setText(String.format(getString(R.string.updater_prefix_latest_version),
                            latestVersionName
                    ) + "\n" + String.format(getString(R.string.updater_msg_new_version),
                            updateType.name().toLowerCase()));
                }

            } catch(PackageManager.NameNotFoundException e) {/* */}

            changelogTitle.setText(String.format(getString(R.string.updater_prefix_changelog_for),
                    latestVersionName));

            changelogTv.setText(

                    String.format(

                            getString(R.string.updater_mask_changelog_inner),

                            latestVersionCode,
                            String.valueOf(latestVersionCode).substring(6, 8),
                            String.valueOf(latestVersionCode).substring(4, 6),
                            String.valueOf(latestVersionCode).substring(0, 4),

                            DownloadUtils.downloadString(UpdaterStorage.URL_CHANGELOG)

                    )

            );

            updaterButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startOverallInstallation(UpdaterStorage.URL_APK);
                }
            });

            webloaded = true;
            if(readyToInstallUrl.length() > 0) startOverallInstallation(readyToInstallUrl);
        }
    }

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_updater);

        Toolbar toolbar = (Toolbar) findViewById(R.id.updaterToolbar);
        setSupportActionBar(toolbar);

        try {
            BarColors.enableBarColoring(getWindow(), R.color.indigo_700);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        } catch(Exception ex) {/* */}


        staticContext = myContext;

        UpdateStatus.applyLanguages();
		
		
		if(RootController.isSuInstalled() && RootController.isBusyboxInstalled()) {
				AlertDialog.Builder adB = new AlertDialog.Builder(staticContext);
				adB.setTitle(getString(R.string.rootutils_root_detect_title))
				   .setMessage(getString(R.string.rootutils_root_detect_message))
				   .setPositiveButton(getString(R.string.answer_yes), new DialogInterface.OnClickListener() {
					   @Override
					   public void onClick(DialogInterface d, int id) {
							if(RootController.requestRoot()) {
								enableRoot = true;
								d.dismiss();
							} else {
								MessageDialog.showDialog(
                                        getString(R.string.rootutils_root_access_failed_title),
                                        getString(R.string.rootutils_root_access_failed), staticContext);
                                d.dismiss();
							}
					   }
				   })
				  .setNegativeButton(getString(R.string.answer_no), new NegativeButtonCancel())
				   ;
				AppCompatDialog aD = adB.create();
				
				aD.show();
			}

        initThisActivity();
		
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		try {
			if(!mgo)onCreateOptionsMenu(null);
		} catch(Exception ex) {/* */}
	}
}