package com.shuayb.capstone.android.crypfolio.Fragments;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.shuayb.capstone.android.crypfolio.DataUtils.JsonUtils;
import com.shuayb.capstone.android.crypfolio.DataUtils.NetworkUtils;
import com.shuayb.capstone.android.crypfolio.DataUtils.RandomUtils;
import com.shuayb.capstone.android.crypfolio.DataViewModel;
import com.shuayb.capstone.android.crypfolio.DatabaseUtils.AppDatabase;
import com.shuayb.capstone.android.crypfolio.DatabaseUtils.Crypto;
import com.shuayb.capstone.android.crypfolio.POJOs.Chart;
import com.shuayb.capstone.android.crypfolio.R;
import com.shuayb.capstone.android.crypfolio.databinding.DetailsFragmentBinding;

import java.util.ArrayList;
import java.util.List;

public class DetailsFragment extends Fragment {
    private static final String TAG = "DetailsFragment";
    private static final String KEY_BUNDLE_CRYPTO = "crypto_key";

    private AppDatabase mDb;
    private DataViewModel mData;

    private DetailsFragmentBinding mBinding;
    private Crypto crypto;
    private boolean isWatchlistItem = false;
    private Menu menu;
    private Chart chart;
    private boolean newCrypto = true;
    private Context mContext;
    private Observer<ArrayList<Crypto>> cryptoObserver;
    private MutableLiveData<ArrayList<Crypto>> cryptosLD;

    public static final DetailsFragment newInstance() {
        DetailsFragment f = new DetailsFragment();
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DetailsFragmentBinding.inflate(inflater, container, false);
        showLoadingScreen();

        mDb = AppDatabase.getInstance(mContext);
        mData = ViewModelProviders.of(getActivity()).get(DataViewModel.class);

        if (crypto != null) {
            setHasOptionsMenu(true);
            initViews(newCrypto, true);
            newCrypto = false;
            setDataObservers();
        } else {
            showErrorScreen();
        }

        return mBinding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();
    }

    private void setDataObservers() {
        cryptoObserver = new Observer<ArrayList<Crypto>>() {
            @Override
            public void onChanged(ArrayList<Crypto> cryptos) {
                Crypto match = null;
                for (Crypto c: cryptos) {
                    if (c.getId().equals(crypto.getId())) {
                        match = c;
                        break;
                    }
                }

                //Only if crypto is still in the top 100 will it update
                if (match != null) {
                    crypto = match;
                    initViews(false, false);
                    if (chart != null) {
                        displayChart(false);
                    }

                } else {
                    Toast.makeText(mContext, getString(R.string.error_toast1), Toast.LENGTH_SHORT).show();
                }
            }
        };
        cryptosLD = mData.getCryptos();
        cryptosLD.observe(this, cryptoObserver);
    }

    @Override
    public void onDestroyView() {
        if (cryptoObserver != null) {
            cryptosLD.removeObserver(cryptoObserver);
            cryptoObserver = null;
        }
        super.onDestroyView();
    }

    //To set the crypto to be displayed
    public void updateCrypto(Crypto crypto) {
        this.crypto = crypto;
        newCrypto = true;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    //Helper method to initialize the views with crypto information
    private void initViews(boolean firstTime, boolean setChartToo) {
        if (setChartToo) {
            setChart(firstTime);
        }
        mBinding.symbolText.setText(crypto.getName() + " (" + crypto.getSymbol().toUpperCase() + ")");
        mBinding.priceText.setText("$" + RandomUtils.getFormattedCurrencyAmount(crypto.getCurrentPrice()));
        mBinding.marketcapText.setText(getString(R.string.details_market_cap) + ": " + crypto.getFormattedMarketcapFull());
        mBinding.high24hText.setText(getString(R.string.details_high) + " 24h: " + crypto.getHigh24h());
        mBinding.low24hText.setText(getString(R.string.details_low) + " 24h: " + crypto.getLow24h());
        mBinding.circSupplyText.setText(getString(R.string.details_circ_supply) + ": " + crypto.getCircSupply());
        mBinding.totalSupplyText.setText(getString(R.string.details_total_supply) + ": " + crypto.getTotalSupply());
        mBinding.athText.setText("ATH: " + crypto.getAth() + " - " + crypto.getAthDate());
        mBinding.lastUpdatedText.setText(getString(R.string.details_last_updated) + ": " + crypto.getLastUpdated());

        //Don't wait for the chart to load too or we might be waiting a long time
        //if we run out of our allotted API calls
        showMainScreen();
    }

    private void setChart(final boolean firstTime) {
        if (chart == null) {
            RequestQueue mRequestQueue = Volley.newRequestQueue(mContext);

            StringRequest mStringRequest = new StringRequest(Request.Method.GET,
                    NetworkUtils.getUrlForChartData(crypto.getId()), new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, "onResponse got this data: " + response);

                    chart = JsonUtils.convertJsonToChart(response);
                    displayChart(firstTime);

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Volley Error!!!!!!!! " + error.getMessage());
                }
            });
            mRequestQueue.add(mStringRequest);
        } else {
            displayChart(firstTime);
        }
    }

    private void displayChart(boolean firstTime) {
        LineDataSet lineDataSet = new LineDataSet(chart.getPrices(), "Prices (USD)");
        if (firstTime) {
            mBinding.chart.animateY(500);
        } else {
            mBinding.chart.animateY(1);
        }
        LineData lineData = new LineData(chart.getTimes(), lineDataSet);
        mBinding.chart.setData(lineData);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.details_menu, menu);
        this.menu = menu;

        setIsWatchlistItem();

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_watchlist) {
            toggleWatchlistItem();
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleWatchlistItem() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (isWatchlistItem) {
                    isWatchlistItem = false;
                    mDb.watchlistDao().deleteWatchlistItem(crypto);
                } else {
                    isWatchlistItem = true;
                    mDb.watchlistDao().insertWatchlistItem(crypto);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                LiveData<List<Crypto>> temp = mDb.watchlistDao().loadAllWatchListItems();
                temp.observe(DetailsFragment.this, new Observer<List<Crypto>>() {
                    @Override
                    public void onChanged(List<Crypto> cryptos) {
                        System.out.println("WATCHLIST ISSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS:");
                        for (Crypto c: cryptos) {
                            System.out.println(c.getId());
                        }
                    }
                });
                MenuItem favButton = menu.findItem(R.id.action_watchlist);

                if (isWatchlistItem) {
                    favButton.setIcon(android.R.drawable.btn_star_big_on);
                } else {
                    favButton.setIcon(android.R.drawable.btn_star_big_off);
                }

                super.onPostExecute(aVoid);
            }
        }.execute();
    }

    //Set the value of the isWatchlistItem boolean by checking if its a watchlist item
    private void setIsWatchlistItem() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (mDb.watchlistDao().getWatchlistItemById(crypto.getId()) != null) {
                    isWatchlistItem = true;
                } else {
                    isWatchlistItem = false;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                MenuItem favButton = menu.findItem(R.id.action_watchlist);

                if (isWatchlistItem) {
                    favButton.setIcon(android.R.drawable.btn_star_big_on);
                } else {
                    favButton.setIcon(android.R.drawable.btn_star_big_off);
                }

                super.onPostExecute(aVoid);
            }
        }.execute();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    private void showLoadingScreen() {
        mBinding.mainContainer.setVisibility(View.GONE);
        mBinding.errorContainer.setVisibility(View.GONE);
        mBinding.loadingContainer.setVisibility(View.VISIBLE);
    }

    private void showMainScreen() {
        mBinding.mainContainer.setVisibility(View.VISIBLE);
        mBinding.errorContainer.setVisibility(View.GONE);
        mBinding.loadingContainer.setVisibility(View.GONE);
    }

    private void showErrorScreen() {
        mBinding.mainContainer.setVisibility(View.GONE);
        mBinding.errorContainer.setVisibility(View.VISIBLE);
        mBinding.loadingContainer.setVisibility(View.GONE);
    }
}
