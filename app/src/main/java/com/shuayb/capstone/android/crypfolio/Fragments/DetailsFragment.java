package com.shuayb.capstone.android.crypfolio.Fragments;

import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.shuayb.capstone.android.crypfolio.DataUtils.JsonUtils;
import com.shuayb.capstone.android.crypfolio.DataUtils.NetworkUtils;
import com.shuayb.capstone.android.crypfolio.DatabaseUtils.AppDatabase;
import com.shuayb.capstone.android.crypfolio.DatabaseUtils.Crypto;
import com.shuayb.capstone.android.crypfolio.POJOs.Chart;
import com.shuayb.capstone.android.crypfolio.R;
import com.shuayb.capstone.android.crypfolio.databinding.DetailsFragmentBinding;

public class DetailsFragment extends Fragment {
    private static final String TAG = "DetailsFragment";

    private AppDatabase mDb;

    DetailsFragmentBinding mBinding;
    Crypto crypto;
    boolean isWatchlistItem = false;
    Menu menu;
    Chart chart;

    public DetailsFragment(Crypto crypto) {
        this.crypto = crypto;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DetailsFragmentBinding.inflate(inflater, container, false);
        mDb = AppDatabase.getInstance(getContext());
        initViews();
        setHasOptionsMenu(true);

        return mBinding.getRoot();
    }


    //Helper method to initialize the views with crypto information
    private void initViews() {
        setChart();
        mBinding.symbolText.setText(crypto.getSymbol());
        mBinding.nameText.setText(crypto.getName());
        mBinding.marketcapText.setText("Market Cap: " + crypto.getFormattedMarketcapFull());
    }

    private void setChart() {
        RequestQueue mRequestQueue = Volley.newRequestQueue(getContext());

        StringRequest mStringRequest = new StringRequest(Request.Method.GET,
                NetworkUtils.getUrlForChartData(crypto.getId()), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, "onResponse got this data: " + response);

                chart = JsonUtils.convertJsonToChart(response);
                LineDataSet lineDataSet = new LineDataSet(chart.getPrices(), "Prices (USD)");
                mBinding.chart.animateY(1000);
                LineData lineData = new LineData(chart.getTimes(), lineDataSet);
                mBinding.chart.setData(lineData);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Volley Error!!!!!!!! " + error.getMessage());
            }
        });
        mRequestQueue.add(mStringRequest);
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
}
