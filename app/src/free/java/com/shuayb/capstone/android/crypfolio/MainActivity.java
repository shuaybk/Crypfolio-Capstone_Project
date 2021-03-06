package com.shuayb.capstone.android.crypfolio;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.material.tabs.TabLayout;
import com.shuayb.capstone.android.crypfolio.CustomAdapters.MarketRecyclerViewAdapter;

import com.shuayb.capstone.android.crypfolio.DataUtils.RandomUtils;
import com.shuayb.capstone.android.crypfolio.Fragments.DetailsFragment;
import com.shuayb.capstone.android.crypfolio.Fragments.MarketviewFragment;
import com.shuayb.capstone.android.crypfolio.Fragments.PortfolioFragment;
import com.shuayb.capstone.android.crypfolio.Fragments.WatchlistFragment;
import com.shuayb.capstone.android.crypfolio.DatabaseUtils.Crypto;
import com.shuayb.capstone.android.crypfolio.databinding.ActivityMainBinding;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity
        implements MarketRecyclerViewAdapter.MarketItemClickListener {

    private static final String TAG = "MainActivity";

    private static final String TITLE_MAIN = "Crypfolio";
    private static final String TITLE_MARKETVIEW = "Market Overview";
    private static final String TITLE_PORTFOLIO = "Portfolio";

    private static final String KEY_BUNDLE_MARKETVIEW_FRAGMENT = "marketview_fragment";
    private static final String KEY_BUNDLE_WATCHLIST_FRAGMENT = "watchlist_fragment";
    private static final String KEY_BUNDLE_PORTFOLIO_FRAGMENT = "portfolio_fragment";
    private static final String KEY_BUNDLE_DETAILS_FRAGMENT = "details_fragment";
    private static final String KEY_BUNDLE_DETAILS_FRAGMENT_SECOND = "details_fragment_second";
    private static final String KEY_BUNDLE_LAST_FRAGMENT_DISPLAYED = "last_fragment";
    private static final String KEY_BUNDLE_TOP_TAB_POS = "top_tab_position";
    private static final String KEY_BUNDLE_BOTTOM_TAB_POS = "bottom_tab_position";
    private static final String KEY_BUNDLE_PREV_BOTTOM_TAB_POS = "previous_bottom_tab_position";
    private static final String KEY_BUNDLE_APPWIDGET_ID = "appwidget_id";
    private static final String KEY_BUNDLE_LAST_CRYPTO_DETAILS = "last_crypto_details";

    private static final String TAG_DETAILS_FRAG_MAIN = "main_details_frag";
    private static final String TAG_DETAILS_FRAG_SECOND = "second_details_frag";

    private static final int FRAG_MARKETVIEW = 1;
    private static final int FRAG_WATCHLIST = 2;
    private static final int FRAG_PORTFOLIO = 3;
    private static final int FRAG_DETAILS = 4;

    private ActivityMainBinding mBinding;
    private int lastFragmentDisplayed = 0;  //Keeps track of what fragment was last displayed
    private MarketviewFragment marketviewFragment;
    private WatchlistFragment watchlistFragment;
    private PortfolioFragment portfolioFragment;
    private DetailsFragment detailsFragment;
    private DetailsFragment detailsFragmentSecond;
    private Thread refreshThread;
    private DataViewModel mData;
    private int prevBottomTabPos = 0;
    private SharedPreferences preferences;
    private int refreshTime;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mData = ViewModelProviders.of(this).get(DataViewModel.class);

        getWidgetInfo();

        if (savedInstanceState != null) {
            restoreSetup(savedInstanceState);
        }
        setCryptoDataObservers();
        setAds();
    }

    private void setAds() {
        MobileAds.initialize(this, getString(R.string.admob_app_id));
        AdRequest adRequest = new AdRequest.Builder().build();
        mBinding.adView.loadAd(adRequest);
    }

    //The observer onChanged method gets called right away on attached
    //regardless of whether or not the data actually changed
    //So we will do our first data refresh here
    private void setCryptoDataObservers() {

        final MutableLiveData<ArrayList<Crypto>> cryptosLD = mData.getCryptos();
        cryptosLD.observe(this, new Observer<ArrayList<Crypto>>() {
            @Override
            public void onChanged(ArrayList<Crypto> cryptos) {
                cryptosLD.removeObserver(this);
                if (marketviewFragment == null) {  //Initial setup
                    initialSetup();
                }
            }
        });
    }

    //Start a thread to refresh the crypto info every x seconds
    //Display error if no connection
    @Override
    protected void onStart() {
        super.onStart();

        String defaultTime = getString(R.string.refresh_time_default_value);
        String timeKey = getString(R.string.key_refresh_time);
        refreshTime = Integer.parseInt(preferences.getString(timeKey, defaultTime)) * 1000;

        if (!isConnectedToInternet()) {
            showError();
        }

        refreshThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!refreshThread.isInterrupted()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isConnectedToInternet()) {
                                    showMainContent();
                                    mData.refreshCryptos(getApplicationContext());
                                } else {
                                    showError();
                                }
                            }
                        });
                        Thread.sleep(refreshTime);
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "Error trying to refresh data in thread: " + e.toString());
                }
            }
        };
        refreshThread.start();
    }

    //Stop the thread that forces data refresh
    @Override
    protected void onStop() {
        super.onStop();
        refreshThread.interrupt();
    }

    private void initialSetup() {
        marketviewFragment = MarketviewFragment.newInstance();
        watchlistFragment = WatchlistFragment.newInstance();
        portfolioFragment = PortfolioFragment.newInstance(appWidgetId);
        detailsFragment = DetailsFragment.newInstance();

        //Attach and detach all fragments so they are in the fragment manager
        //This manages their lifecycle automatically
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .add(R.id.frag_main, marketviewFragment)
                .commit();
        fm.beginTransaction()
                .detach(marketviewFragment)
                .add(R.id.frag_main, watchlistFragment)
                .commit();
        fm.beginTransaction()
                .detach(watchlistFragment)
                .add(R.id.frag_main, portfolioFragment)
                .commit();
        fm.beginTransaction()
                .detach(portfolioFragment)
                .add(R.id.frag_main, detailsFragment)
                .commit();
        fm.beginTransaction()
                .detach(detailsFragment)
                .commit();

        if (getString(R.string.isTabletLandscape).equals("true")) {
            setSecondFragmentForTablet();
        }

        //If widget didn't start the app, go to MarketView
        //Otherwise go to Portfolio
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setMarketviewFragment();
            setupBottomTabs(0);
            setupTopTabs(0);
        } else {
            setPortfolioFragment();
            setupBottomTabs(1);
            setupTopTabs(0);
        }
    }

    private void restoreSetup(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        marketviewFragment = (MarketviewFragment)fm.getFragment(savedInstanceState, KEY_BUNDLE_MARKETVIEW_FRAGMENT);
        watchlistFragment = (WatchlistFragment)fm.getFragment(savedInstanceState, KEY_BUNDLE_WATCHLIST_FRAGMENT);
        portfolioFragment = (PortfolioFragment)fm.getFragment(savedInstanceState, KEY_BUNDLE_PORTFOLIO_FRAGMENT);
        detailsFragment = (DetailsFragment)fm.getFragment(savedInstanceState, KEY_BUNDLE_DETAILS_FRAGMENT);
        detailsFragmentSecond = (DetailsFragment)fm.getFragment(savedInstanceState, KEY_BUNDLE_DETAILS_FRAGMENT_SECOND); //might be null


        lastFragmentDisplayed = savedInstanceState.getInt(KEY_BUNDLE_LAST_FRAGMENT_DISPLAYED);
        int topTabPosition = savedInstanceState.getInt(KEY_BUNDLE_TOP_TAB_POS);
        int bottomTabPosition = savedInstanceState.getInt(KEY_BUNDLE_BOTTOM_TAB_POS);
        prevBottomTabPos = savedInstanceState.getInt(KEY_BUNDLE_PREV_BOTTOM_TAB_POS);
        appWidgetId = savedInstanceState.getInt(KEY_BUNDLE_APPWIDGET_ID);
        Crypto lastDetailCryptoDisplayed = savedInstanceState.getParcelable(KEY_BUNDLE_LAST_CRYPTO_DETAILS);
        detailsFragment.updateCrypto(lastDetailCryptoDisplayed);


        setupBottomTabs(bottomTabPosition);
        setupTopTabs(topTabPosition);

        //Set the current fragment to display
        //Makes sure we don't display the details fragment as the main fragment if we are in a dual layout
        Fragment currFrag = fm.findFragmentById(R.id.frag_main);
        if (currFrag instanceof MarketviewFragment) {
            setMarketviewFragmentViews();
        } else if (currFrag instanceof WatchlistFragment) {
            setWatchlistFragmentViews();
        } else if (currFrag instanceof PortfolioFragment) {
            setPortfolioFragmentViews();
        } else if (currFrag instanceof DetailsFragment) {
            if (getString(R.string.isTabletLandscape).equals("true")) {
                if (lastFragmentDisplayed == FRAG_MARKETVIEW) {
                    setMarketviewFragment();
                } else if (lastFragmentDisplayed == FRAG_WATCHLIST) {
                    setWatchlistFragment();
                } else if (lastFragmentDisplayed == FRAG_PORTFOLIO) {
                    setPortfolioFragment();
                } else {
                    setMarketviewFragment();
                }
            } else {
                setDetailsFragmentViews();
                setDetailsFragment(lastDetailCryptoDisplayed);
            }
        }

        if (getString(R.string.isTabletLandscape).equals("true")) {
            resetSecondFragmentForTablet(lastDetailCryptoDisplayed);
        }
    }


    //Method to set up the behaviour of the tabs
    //The tabs determine which fragment will be displayed
    //Except for settings, which launches a new activity
    private void setupBottomTabs(int pos) {

        //Set previous tab position from savedInstanceState, if exists
        if (pos >= 0) {
            mBinding.tabLayoutBottom.getTabAt(pos).select();
        }

        mBinding.tabLayoutBottom.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                if (!isConnectedToInternet()) {
                    showError();
                }

                switch (mBinding.tabLayoutBottom.getSelectedTabPosition()) {
                    case 0:     //Markets tab
                        if (mBinding.tabLayoutTop.getSelectedTabPosition() == 0) {
                            setMarketviewFragment();
                        } else {
                            setWatchlistFragment();
                        }
                        prevBottomTabPos = 0;
                        break;
                    case 1:     //Portfolio tab
                        setPortfolioFragment();
                        prevBottomTabPos = 1;
                        break;
                    case 2:     //Settings tab
                        mBinding.tabLayoutBottom.getTabAt(prevBottomTabPos).select();
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                //Nothing to do here
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                //Nothing to do here
            }
        });
    }


    private void setupTopTabs(int pos) {
        if (pos >= 0) {
            mBinding.tabLayoutTop.getTabAt(pos).select();
        }
        mBinding.tabLayoutTop.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                if (!isConnectedToInternet()) {
                    showError();
                }

                switch (mBinding.tabLayoutTop.getSelectedTabPosition()) {
                    case 0:  //Market View tab
                        setMarketviewFragment();
                        break;
                    case 1:  //Watchlist tab
                        setWatchlistFragment();
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                //Nothing to do here
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                //Nothing to do here
            }
        });
    }

    //Helper method to set the displayed fragment in the main activity
    private void setFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();

        //Store which fragment was last displayed before we change the fragment
        setValueOfLastFragmentDisplayed(fm);

        Fragment currFrag = fm.findFragmentById(R.id.frag_main);

        if (currFrag != null) {
            fm.beginTransaction()
                    .detach(currFrag)
                    .attach(fragment)
                    .commit();
        } else {
            fm.beginTransaction()
                    .attach(fragment)
                    .commit();
        }
    }

    private void setSecondFragmentForTablet() {
        FragmentManager fm = getSupportFragmentManager();

        detailsFragmentSecond = DetailsFragment.newInstance();
        fm.beginTransaction()
                .add(R.id.frag_second, detailsFragmentSecond)
                .commit();
    }

    private void resetSecondFragmentForTablet(Crypto crypto) {
        FragmentManager fm = getSupportFragmentManager();

        Fragment currSecondFrag = fm.findFragmentById(R.id.frag_second);
        if (detailsFragmentSecond == null) {
            System.out.println("detailsFragmentSecond is initially null");
            if (currSecondFrag != null) {
                System.out.println("currSecondFrag has a value");
                detailsFragmentSecond = (DetailsFragment)currSecondFrag;
            }  else {
                System.out.println("currSecondFrag is null");
                detailsFragmentSecond = DetailsFragment.newInstance();
            }
        } else {
            System.out.println("detailsFragmentSecond initially has a value");
        }

        if (crypto != null) {
            detailsFragmentSecond.updateCrypto(crypto);
            System.out.println("The second fragment crypto isssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss " + detailsFragmentSecond.getCrypto().getId());
        }

        if (currSecondFrag != null) {
            System.out.println("The second fragment has a value");
            fm.beginTransaction()
                    .detach(currSecondFrag) //To force view to reset
                    .attach(detailsFragmentSecond)
                    .commit();
        } else {
            System.out.println("The second fragment is null");
            fm.beginTransaction()
                    .add(R.id.frag_second, detailsFragmentSecond)
                    .commit();
        }
    }

    //Helper method that sets the correct value for lastFragmentDisplayed
    private void setValueOfLastFragmentDisplayed(FragmentManager fm) {
        Fragment fragment = fm.findFragmentById(R.id.frag_main);

        if (fragment == null || fragment instanceof MarketviewFragment) {
            lastFragmentDisplayed = FRAG_MARKETVIEW;  //Default state is marketview fragment if no previous fragment exists
        } else if (fragment instanceof WatchlistFragment) {
            lastFragmentDisplayed = FRAG_WATCHLIST;
        } else if (fragment instanceof  PortfolioFragment) {
            lastFragmentDisplayed = FRAG_PORTFOLIO;
        } else if (fragment instanceof DetailsFragment) {
            lastFragmentDisplayed = FRAG_DETAILS;
        }
    }

    public boolean isConnectedToInternet() {
        ConnectivityManager conMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMgr.getActiveNetworkInfo();

        if (netInfo == null) {
            return false;
        }
        return true;
    }

    private void setMarketviewFragment() {
        setFragment(marketviewFragment);
        setMarketviewFragmentViews();
    }

    private void setPortfolioFragment() {
        setFragment(portfolioFragment);
        setPortfolioFragmentViews();
    }

    private void setWatchlistFragment() {
        setFragment(watchlistFragment);
        setWatchlistFragmentViews();
    }

    private void setDetailsFragment(Crypto crypto) {
        FragmentManager fm = getSupportFragmentManager();

        if (getString(R.string.isTabletLandscape).equals("false")) {
            System.out.println("setDetailsFragment regular viewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");
            detailsFragment.updateCrypto(crypto);
            setFragment(detailsFragment);
            setDetailsFragmentViews();
        } else {
            System.out.println("setDetailsFragment dual viewwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");
            detailsFragmentSecond.updateCrypto(crypto);
            resetSecondFragmentForTablet(crypto);
        }
    }


    private void setMarketviewFragmentViews() {
        mBinding.tabLayoutTop.setVisibility(View.VISIBLE);
        mBinding.tabLayoutBottom.setVisibility(View.VISIBLE);

        //Hide the back button if it was shown
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(TITLE_MARKETVIEW);
    }

    private void setPortfolioFragmentViews() {
        mBinding.tabLayoutTop.setVisibility(View.GONE);
        mBinding.tabLayoutBottom.setVisibility(View.VISIBLE);

        //Hide the back button if it was shown
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(TITLE_PORTFOLIO);
    }

    private void setWatchlistFragmentViews() {
        mBinding.tabLayoutTop.setVisibility(View.VISIBLE);
        mBinding.tabLayoutBottom.setVisibility(View.VISIBLE);

        //Hide the back button if it was shown
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(TITLE_MARKETVIEW);
    }

    private void setDetailsFragmentViews() {
        mBinding.tabLayoutTop.setVisibility(View.GONE);
        mBinding.tabLayoutBottom.setVisibility(View.GONE);

        //Enable the back button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (getString(R.string.isTabletLandscape).equals("false")) {
            getSupportActionBar().setTitle(TITLE_MAIN);
        }
    }

    //Set behavior of actionbar back button
    //Back button only displays on the main activity if we are in details fragment
    @Override
    public boolean onSupportNavigateUp(){
        if (lastFragmentDisplayed == FRAG_WATCHLIST) {
            mBinding.tabLayoutTop.getTabAt(1).select();
            setWatchlistFragment();
        } else {
            mBinding.tabLayoutTop.getTabAt(0).select();
            setMarketviewFragment();
        }
        return true;
    }

    @Override
    public void onMarketItemClick(Crypto crypto) {
        //Display the details tab for the selected crypto
        //Also hide the top and bottom tabs
        if (isConnectedToInternet()) {
            setDetailsFragment(crypto);
        } else {
            showError();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        getSupportFragmentManager().putFragment(outState, KEY_BUNDLE_MARKETVIEW_FRAGMENT, marketviewFragment);
        getSupportFragmentManager().putFragment(outState, KEY_BUNDLE_WATCHLIST_FRAGMENT, watchlistFragment);
        getSupportFragmentManager().putFragment(outState, KEY_BUNDLE_PORTFOLIO_FRAGMENT, portfolioFragment);
        getSupportFragmentManager().putFragment(outState, KEY_BUNDLE_DETAILS_FRAGMENT, detailsFragment);
        if (detailsFragmentSecond != null) {
            getSupportFragmentManager().putFragment(outState, KEY_BUNDLE_DETAILS_FRAGMENT_SECOND, detailsFragmentSecond);
        }

        outState.putInt(KEY_BUNDLE_LAST_FRAGMENT_DISPLAYED, lastFragmentDisplayed);
        outState.putInt(KEY_BUNDLE_TOP_TAB_POS, mBinding.tabLayoutTop.getSelectedTabPosition());
        outState.putInt(KEY_BUNDLE_BOTTOM_TAB_POS, mBinding.tabLayoutBottom.getSelectedTabPosition());
        outState.putInt(KEY_BUNDLE_PREV_BOTTOM_TAB_POS, prevBottomTabPos);
        outState.putInt(KEY_BUNDLE_APPWIDGET_ID, appWidgetId);

        if (getString(R.string.isTabletLandscape).equals("false")) {
            outState.putParcelable(KEY_BUNDLE_LAST_CRYPTO_DETAILS, detailsFragment.getCrypto());
        } else {
            outState.putParcelable(KEY_BUNDLE_LAST_CRYPTO_DETAILS, detailsFragmentSecond.getCrypto());
        }
    }

    private void showError() {
        mBinding.mainContainer.setVisibility(View.GONE);
        mBinding.errorContainer.setVisibility(View.VISIBLE);
    }

    private void showMainContent() {
        mBinding.errorContainer.setVisibility(View.GONE);
        mBinding.mainContainer.setVisibility(View.VISIBLE);
    }

    private void getWidgetInfo() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
    }
}
