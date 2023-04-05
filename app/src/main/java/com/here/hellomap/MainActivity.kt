/*
 * Copyright (C) 2019-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.hellomap

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.here.hellomap.PermissionsRequestor.ResultListener
import com.here.sdk.core.GeoCoordinates
import com.here.sdk.core.LanguageCode
import com.here.sdk.core.engine.SDKNativeEngine
import com.here.sdk.core.engine.SDKOptions
import com.here.sdk.core.errors.InstantiationErrorException
import com.here.sdk.location.LocationEngine
import com.here.sdk.mapview.MapMeasure
import com.here.sdk.mapview.MapScheme
import com.here.sdk.mapview.MapView
import com.here.sdk.mapview.MapView.OnReadyListener
import com.here.sdk.search.AddressQuery
import com.here.sdk.search.SearchCallback
import com.here.sdk.search.SearchEngine
import com.here.sdk.search.SearchOptions


class MainActivity : AppCompatActivity() {
    private var permissionsRequestor: PermissionsRequestor? = null
    private lateinit var mapView: MapView

    private lateinit var bt_select: Button
    private lateinit var tv_select: TextView

    var index = 0

    lateinit var reverseGeocodingOptions : SearchOptions
    lateinit var searchEngine : SearchEngine

    lateinit var geoCoordinateList : MutableList<GeoCoordinates>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Usually, you need to initialize the HERE SDK only once during the lifetime of an application.
        initializeHERESDK()

        setContentView(R.layout.activity_main)

        // Get a MapView instance from the layout.
        mapView = findViewById(R.id.map_view)
        mapView?.onCreate(savedInstanceState)
        mapView?.setOnReadyListener(OnReadyListener {
            // This will be called each time after this activity is resumed.
            // It will not be called before the first map scene was loaded.
            // Any code that requires map data may not work as expected beforehand.
            Log.d(TAG, "HERE Rendering Engine attached.")
        })
        bt_select = findViewById(R.id.bt_select)
        bt_select.isEnabled = false
//        tv_select = findViewById(R.id.tv_select)

        handleAndroidPermissions()

        searchEngine = SearchEngine()
        reverseGeocodingOptions = SearchOptions()

        geoCoordinateList = mutableListOf<GeoCoordinates>(
            GeoCoordinates(47.0517107724, 6.9769611437),
            GeoCoordinates(47.0518965562, 6.9770253497),
            GeoCoordinates(47.0520204176, 6.9770695249),
            GeoCoordinates(47.0520964606, 6.9771058860),
            GeoCoordinates(47.0522570784, 6.9772032121),

            GeoCoordinates(47.0492166675, 6.9719076559),
            GeoCoordinates(47.0217685943, 6.9223475258),
            GeoCoordinates(47.0091005782, 6.9201392406),

            GeoCoordinates(47.0040310719, 6.9248685302),
            GeoCoordinates(46.9992430310, 6.9316281074),
            GeoCoordinates(46.9929703142, 6.9294318683),
        )

        bt_select.setOnClickListener {
            reverseGeocodingOptions.languageCode = LanguageCode.EN_GB
            reverseGeocodingOptions.maxItems = 1

            getAddressForCoordinates()
        }
    }



    private fun getAddressForCoordinates() {
        if (index >= geoCoordinateList.size) {
            index = 0
            return
        }
        searchEngine.search(geoCoordinateList[index], reverseGeocodingOptions, addressSearchCallback)
        index++
    }

    private val addressSearchCallback = SearchCallback { searchError, list ->
        if (searchError != null) {
            Toast.makeText(getContext(), "Reverse geocoding Error: $searchError", Toast.LENGTH_SHORT).show()
            return@SearchCallback
        }

        // If error is null, list is guaranteed to be not empty.
        val address = list!![0].address.addressText
        val altitude = list!![0].geoCoordinates?.altitude
        Toast.makeText(getContext(), "address: $address\naltitude: $altitude", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "address: $address\naltitude: $altitude")
        getAddressForCoordinates()
    }
    fun getContext(): Context? {
        return this as Context
    }

    lateinit var locationEngine: LocationEngine
    private fun initializeHERESDK() {
        // Set your credentials for the HERE SDK.
        var accessKeyID = "foo"
        var accessKeySecret = "bar"

        var options = SDKOptions(accessKeyID, accessKeySecret)
        try {
            var context = this
            SDKNativeEngine.makeSharedInstance(context, options)
        } catch (e: InstantiationErrorException) {
            throw RuntimeException("Initialization of HERE SDK failed: " + e.error.name)
        }

        try {
            locationEngine = LocationEngine()
        } catch (e: InstantiationErrorException) {
            throw java.lang.RuntimeException("Initialization of LocationEngine failed: " + e.message)
        }
    }

    private fun handleAndroidPermissions() {
        permissionsRequestor = PermissionsRequestor(this)
        permissionsRequestor?.request(object : ResultListener {
            override fun permissionsGranted() {
                loadMapScene()
                bt_select.isEnabled = true
            }

            override fun permissionsDenied() {
                Log.e(TAG, "Permissions denied by user.")
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsRequestor?.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun loadMapScene() {
        // Load a scene from the HERE SDK to render the map with a map scheme.
        mapView?.mapScene?.loadScene(MapScheme.NORMAL_DAY) { mapError ->
            if (mapError == null) {
                val distanceInMeters = (1000 * 10).toDouble()
                val mapMeasureZoom = MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters)
                mapView?.camera?.lookAt(GeoCoordinates(47.0517107724, 6.9769611437), mapMeasureZoom)
            } else {
                Log.d(TAG, "Loading map failed: mapError: " + mapError.name)
            }
        }
    }

    override fun onPause() {
//        mapView?.onPause()
        super.onPause()
    }

    override fun onResume() {
//        mapView?.onResume()
        super.onResume()
    }

    override fun onDestroy() {
//        mapView?.onDestroy()
        disposeHERESDK()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
//        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun disposeHERESDK() {
        // Free HERE SDK resources before the application shuts down.
        // Usually, this should be called only on application termination.
        // Afterwards, the HERE SDK is no longer usable unless it is initialized again.
        SDKNativeEngine.getSharedInstance()?.dispose()
        // For safety reasons, we explicitly set the shared instance to null to avoid situations,
        // where a disposed instance is accidentally reused.
        SDKNativeEngine.setSharedInstance(null)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
