package com.shuayb.capstone.android.crypfolio.Fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.shuayb.capstone.android.crypfolio.AddPortfolioItemActivity;
import com.shuayb.capstone.android.crypfolio.CustomAdapters.PortfolioRecyclerViewAdapter;
import com.shuayb.capstone.android.crypfolio.DataUtils.JsonUtils;
import com.shuayb.capstone.android.crypfolio.DataUtils.NetworkUtils;
import com.shuayb.capstone.android.crypfolio.DatabaseUtils.Crypto;
import com.shuayb.capstone.android.crypfolio.POJOs.PortfolioItem;
import com.shuayb.capstone.android.crypfolio.databinding.PortfolioFragmentBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class PortfolioFragment extends Fragment
        implements PortfolioRecyclerViewAdapter.PortfolioItemClickListener {

    private static final String TAG = "PortfolioFragment";

    private static final String KEY_BUNDLE_ARRAYLIST = "crypto_list";
    private static final String KEY_CRYPTO_ID = "key_crypto_id";
    private static final String KEY_AMOUNT = "key_amount";
    private static final String KEY_PURCHASE_PRICE = "key_purchase_price";
    private static final int RC_SIGN_IN = 123;
    private static final int RC_ADD_PORTFOLIO_ITEM = 456;
    private static final int DB_AMOUNT_INDEX = 0;
    private static final int DB_PRICE_INDEX = 1;

    private PortfolioFragmentBinding mBinding;
    private FirebaseAuth authFb;
    private FirebaseUser userFb;
    private FirebaseFirestore dbf;
    private DocumentReference portfolioRef;
    private ArrayList<Crypto> cryptos;
    private HashMap<String, PortfolioItem> portfolioItems;

    List<AuthUI.IdpConfig> providers = Arrays.asList(
            new AuthUI.IdpConfig.EmailBuilder().build());

    public static final PortfolioFragment newInstance(ArrayList<Crypto> list) {
        PortfolioFragment f = new PortfolioFragment();
        Bundle bundle = new Bundle(1);
        bundle.putParcelableArrayList(KEY_BUNDLE_ARRAYLIST, list);
        f.setArguments(bundle);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authFb = FirebaseAuth.getInstance();
        dbf = FirebaseFirestore.getInstance();
        cryptos = getArguments().getParcelableArrayList(KEY_BUNDLE_ARRAYLIST);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = PortfolioFragmentBinding.inflate(inflater, container, false);

        userFb = authFb.getCurrentUser();

        initViews();

        return mBinding.getRoot();
    }

    private void initViews() {

        if (userFb == null) {
            mBinding.signInPrompt.setVisibility(View.VISIBLE);
            mBinding.mainContentContainer.setVisibility(View.GONE);
        } else {
            portfolioRef = dbf.collection("users").document(userFb.getUid());
            mBinding.signInPrompt.setVisibility(View.GONE);
            mBinding.mainContentContainer.setVisibility(View.VISIBLE);
            fetchPortfolioInfo();
        }

        mBinding.signInPrompt.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setAvailableProviders(providers)
                                .build(),
                        RC_SIGN_IN);
            }
        });
        mBinding.fabAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), AddPortfolioItemActivity.class);
                intent.putParcelableArrayListExtra(KEY_BUNDLE_ARRAYLIST, cryptos);
                startActivityForResult(intent, RC_ADD_PORTFOLIO_ITEM);
            }
        });
    }

    private void initRecyclerView(ArrayList<Crypto> portfolioList) {
        PortfolioRecyclerViewAdapter adapter = new PortfolioRecyclerViewAdapter(getContext(), portfolioList, portfolioItems, this);
        mBinding.recyclerView.setAdapter(adapter);
        mBinding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    //First get the Portfolio info from Firebase here
    private void fetchPortfolioInfo() {
        portfolioRef.get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            Map<String, Object> docData = task.getResult().getData();
                            if (docData == null) {
                                docData = new HashMap<>();
                            }
                            generatePortfolioItems(docData);
                        } else {
                            portfolioItems = new HashMap<>(); //Empty list
                            mBinding.signInPrompt.setVisibility(View.GONE);
                            mBinding.mainContentContainer.setVisibility(View.GONE);
                            mBinding.errorMessage.setVisibility(View.VISIBLE);
                            Toast.makeText(getContext(), "Error: Could not fetch Portfolio", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    //Generate the portfolio items from Firebase info
    //and fetch the remaining needed info from CoinGecko
    private void generatePortfolioItems(Map<String, Object> dataMap) {
        portfolioItems = new HashMap<>();
        String id;
        String name;
        String image;
        double amount;
        double avgPrice;
        double currentPrice;

        StringBuilder ids = new StringBuilder("");

        for (String key: dataMap.keySet()) {
            List<Double> list = (ArrayList<Double>)(dataMap.get(key));
            id = key;
            name = "";
            image = "";
            amount = list.get(DB_AMOUNT_INDEX);
            avgPrice = list.get(DB_PRICE_INDEX);
            currentPrice = -1;
            PortfolioItem item = new PortfolioItem(id, name, image, amount, avgPrice, currentPrice);
            portfolioItems.put(id, item);

            if (ids.length() == 0) {
                ids.append(id);
            } else {
                ids.append(",").append(id);
            }
        }

        //Look up the price info for our portfolio items (if any)
        if (ids.length() > 0) {
            RequestQueue mRequestQueue = Volley.newRequestQueue(getContext());

            StringRequest mStringRequest = new StringRequest(Request.Method.GET,
                    NetworkUtils.getUrlForPortfolioData(ids.toString()), new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, "onResponse got this data: " + response);
                    ArrayList<Crypto> portfolioList = JsonUtils.convertJsonToCryptoList(response);

                    for (Crypto c : portfolioList) {
                        PortfolioItem item = portfolioItems.get(c.getId());
                        item.setName(c.getName());
                        item.setImage(c.getImage());
                        item.setCurrentPrice(c.getCurrentPrice());
                        portfolioItems.put(c.getId(), item);
                    }
                    //Now we have all the info needed, can finish initializing views
                    initRecyclerView(portfolioList);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Volley Error in generatePortfolioItems!!!!!!!! " + error.getMessage());
                }
            });
            mRequestQueue.add(mStringRequest);
        }
    }


    private void addPortfolioItemOnCloud(final String cryptoId, final double amount, final double purchasePrice) {
        portfolioRef.get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot result = task.getResult();

                            Map<String, Object> data = task.getResult().getData();

                            if (data == null) {
                                data = new HashMap<>();
                            }

                            if (data.containsKey(cryptoId)) { //Update the existing portfolio item
                                List<Double> list = (List<Double>)(data.get(cryptoId));
                                double oldAmount = list.get(DB_AMOUNT_INDEX);
                                double totalAmount = amount + oldAmount;
                                double oldPrice = list.get(DB_PRICE_INDEX);
                                double avgPrice = (oldAmount/totalAmount)*oldPrice + (amount/totalAmount)*purchasePrice;
                                list.set(DB_AMOUNT_INDEX, totalAmount);
                                list.set(DB_PRICE_INDEX, avgPrice);
                                data.put(cryptoId, list);
                                portfolioRef.set(data);

                            } else { //Add a new portfolio item
                                List<Double> list = new ArrayList<Double>();
                                list.add(DB_AMOUNT_INDEX, amount);
                                list.add(DB_PRICE_INDEX, purchasePrice);
                                data.put(cryptoId, list);
                                portfolioRef.set(data);
                                Toast.makeText(getContext(), "Added to DB!!", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "Error adding Portfolio item", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case RC_SIGN_IN:
                IdpResponse response = IdpResponse.fromResultIntent(data);

                if (resultCode == RESULT_OK) {
                    //Successfully signed in
                    userFb = authFb.getCurrentUser();
                    mBinding.signInPrompt.setVisibility(View.GONE);
                    mBinding.mainContentContainer.setVisibility(View.VISIBLE);
                    //refresh all views
                    initViews();
                } else {
                    Toast.makeText(getContext(), "Unable to sign in!", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Authentication failed!!!");
                }
                break;

            case RC_ADD_PORTFOLIO_ITEM:
                if (resultCode == RESULT_OK) {
                    String cryptoId = data.getStringExtra(KEY_CRYPTO_ID);
                    double amount = data.getDoubleExtra(KEY_AMOUNT, 0);
                    double purchasePrice = data.getDoubleExtra(KEY_PURCHASE_PRICE, 0);

                    addPortfolioItemOnCloud(cryptoId, amount, purchasePrice);

                } else if (resultCode == RESULT_CANCELED) {
                    //Do nothing, this is fine
                } else {
                    Toast.makeText(getContext(), "Unexpected result!", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Add Portfolio item activity returned an unexpected result.  requestCode = " + requestCode);
                }
                break;
        }
    }

    @Override
    public void onPortfolioItemClick(PortfolioItem portfolioItem) {
        Toast.makeText(getContext(), "You click Portfolio item " + portfolioItem.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }
}
