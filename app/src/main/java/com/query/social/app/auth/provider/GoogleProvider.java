/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.query.social.app.auth.provider;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;
import com.query.social.app.R;
import com.query.social.app.auth.AuthUI;
import com.query.social.app.auth.AuthUI.IdpConfig;
import com.query.social.app.auth.IdpResponse;
import com.query.social.app.auth.util.GoogleApiHelper;

public class GoogleProvider implements IdpProvider, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "GoogleProvider";
    private static final int RC_SIGN_IN = 20;
    private static final String ERROR_KEY = "error";

    private GoogleApiClient mGoogleApiClient;
    private FragmentActivity mActivity;
    private IdpConfig mIdpConfig;
    private IdpCallback mIdpCallback;
    private boolean mSpecificAccount;

    public GoogleProvider(FragmentActivity activity, IdpConfig idpConfig) {
        this(activity, idpConfig, null);
    }

    public GoogleProvider(FragmentActivity activity, IdpConfig idpConfig, @Nullable String email) {
        mActivity = activity;
        mIdpConfig = idpConfig;
        mSpecificAccount = !TextUtils.isEmpty(email);
        mGoogleApiClient = new GoogleApiClient.Builder(mActivity)
                .enableAutoManage(mActivity, GoogleApiHelper.getSafeAutoManageId(), this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, getSignInOptions(email))
                .build();
    }

    public static AuthCredential createAuthCredential(IdpResponse response) {
        return GoogleAuthProvider.getCredential(response.getIdpToken(), null);
    }

    private GoogleSignInOptions getSignInOptions(@Nullable String email) {
        String clientId = mActivity.getString(R.string.default_web_client_id);

        GoogleSignInOptions.Builder builder =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestIdToken(clientId);

        // Add additional scopes
        for (String scopeString : mIdpConfig.getScopes()) {
            builder.requestScopes(new Scope(scopeString));
        }

        if (!TextUtils.isEmpty(email)) {
            builder.setAccountName(email);
        }

        return builder.build();
    }

    @Override
    public String getName(Context context) {
        return context.getString(R.string.idp_name_google);
    }

    @Override
    @AuthUI.SupportedProvider
    public String getProviderId() {
        return GoogleAuthProvider.PROVIDER_ID;
    }

    @Override
    @LayoutRes
    public int getButtonLayout() {
        return R.layout.idp_button_google;
    }

    @Override
    public void setAuthenticationCallback(IdpCallback callback) {
        mIdpCallback = callback;
    }

    public void disconnect() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    private IdpResponse createIdpResponse(GoogleSignInAccount account) {
        return new IdpResponse.Builder(GoogleAuthProvider.PROVIDER_ID, account.getEmail())
                .setToken(account.getIdToken())
                .build();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result != null) {
                if (result.isSuccess()) {
                    if (mSpecificAccount) {
                        Toast.makeText(
                                mActivity,
                                mActivity.getString(
                                        R.string.signed_in_with_specific_account,
                                        result.getSignInAccount().getEmail()),
                                Toast.LENGTH_SHORT).show();
                    }
                    mIdpCallback.onSuccess(createIdpResponse(result.getSignInAccount()));
                } else {
                    onError(result);
                }
            } else {
                onError("No result found in intent");
            }
        }
    }

    @Override
    public void startLogin(Activity activity) {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        activity.startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void onError(GoogleSignInResult result) {
        Status status = result.getStatus();

        if (status.getStatusCode() == CommonStatusCodes.INVALID_ACCOUNT) {
            mGoogleApiClient.stopAutoManage(mActivity);
            mGoogleApiClient.disconnect();
            mGoogleApiClient = new GoogleApiClient.Builder(mActivity)
                    .enableAutoManage(mActivity, GoogleApiHelper.getSafeAutoManageId(), this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, getSignInOptions(null))
                    .build();
            startLogin(mActivity);
        } else {
            onError(status.getStatusCode() + " " + status.getStatusMessage());
        }
    }

    private void onError(String errorMessage) {
        Log.e(TAG, "Error logging in with Google. " + errorMessage);
        Bundle extra = new Bundle();
        extra.putString(ERROR_KEY, errorMessage);
        mIdpCallback.onFailure(extra);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed:" + connectionResult);
    }
}

