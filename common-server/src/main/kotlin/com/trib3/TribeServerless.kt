package com.trib3

import com.amazonaws.serverless.proxy.jersey.JerseyLambdaContainerHandler
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

/**
 * Exposes the dropwizard application's jersey context as a serverless handler
 */
class TribeServerless: RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    val proxy: JerseyLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse>

    constructor() {
        val app = TribeApplication.init()
        proxy = JerseyLambdaContainerHandler.getAwsProxyHandler(app.env!!.jersey().resourceConfig)
    }

    override fun handleRequest(input: AwsProxyRequest?, context: Context?): AwsProxyResponse {
        return proxy.proxy(input, context)
    }

}