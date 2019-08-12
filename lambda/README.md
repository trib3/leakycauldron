lambda
======
Provides the application infrastructure for running on [AWS Lambda](https://aws.amazon.com/lambda/)

#### Execution
After declaring a runtime dependency on the lambda module, an application's
shaded `.jar` can be uploaded to [AWS Lambda](https://aws.amazon.com/lambda/) to
execute behind the Amazon API Gateway.  It is recommended to add `lambda` to the 
active environment list (eg, `ENV=prod,lambda`).  The lambda handler method should 
be set to `com.trib3.lambda.TribeServerless::handleRequest`.
