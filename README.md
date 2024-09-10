# Syncloop Core

[![Maven Central](https://img.shields.io/maven-central/v/com.syncloop.middleware/core.svg?style=flat)](https://search.maven.org/artifact/com.syncloop.middleware/core) 
[![Latest release](https://img.shields.io/github/v/release/syncloop-dev/syncloop-core)](https://github.com/ekamiddleware/syncloop-core/releases/latest)
<a href="https://www.syncloop.com/"><img src="https://www.syncloop.com/assets/img/logo/syncloop-logo.svg" align="right" width="200" ></a>
[![Docs](https://img.shields.io/badge/docs-v1.6.7-brightgreen.svg?style=flat)](https://docs.syncloop.com/?utm_source=Website&utm_medium=click&utm_id=documentation&utm_content=github)

<a href="https://discord.com/invite/EnazFJpGQs"><img src="https://img.shields.io/badge/chat-discord-brightgreen.svg?logo=discord&style=flat"></a>
<a href="https://twitter.com/Syncloop_dev"><img src="https://img.shields.io/badge/Follow-SyncloopDev-blue.svg?style=flat&logo=twitter"></a>
<a href="https://www.youtube.com/@syncloop"><img src="https://img.shields.io/badge/YouTube-Tutorials-yellow.svg?style=flat"></a>

Syncloop Core is an advanced extension for the [Syncloop Server](https://github.com/syncloop-dev/syncloop-server) repository, designed to enhance the server runtime for Syncloop's embedded solutions. Our technology optimizes performance and scalability, making it the ideal choice for seamless integration and efficient operation in diverse environments.

Read more at [syncloop.com](https://www.syncloop.com/syncloop-embedded.html) and the [docs](https://docs.syncloop.com/docs/syncloop-embedded-integration/introduction?utm_source=Website&utm_medium=click&utm_id=documentation&utm_content=github).

------------------

## Introduction

Syncloop also provides the client and server-side SDK that allows easy integration of the Syncloop platform to your existing applications. Everything can be deployed within your current application.

The main purpose of the SDK is you don't require a separate managed container/server to create APIs/Rules. You can create and execute APIs/Rules within your existing application.

*i.e. If you have a spring-boot application & you want to use Syncloop in the same application. So you can use this SDK.*

Syncloop SDK comes in two forms: **UI SDK** & **Server Runtime**. This document will explain how to use and integrate both SDKs.

### Prerequisite

1. **UI SDK**: Syncloop Embedded UI Editor is only available for integration as in iframe and not available for responsive screens. This documentation will explain how to integrate and invoke APIs from the editor.
2. **Server Runtime**: Syncloop platform runs on Java 17, & needs a JRE for its server runtime.


### Request a License

You can request a new enterprise or open source license for embedded [Get a license](https://www.syncloop.com/request-enterprise-license.html)

### UI SDK

Syncloop UI SDK is used in HTML. It enables the Syncloop UI editor, which is used to create the API/rule. It enables all the major UI components available in the Syncloop workspace, and the usage of all components is similar. The following steps enable and integrate the editor in your application.

1. Download the [SDK](https://www.syncloop.com/download-sdk.html).
2. Extract the syncloop-ui-sdk zip. (It would contain 2 zip files inside the SDK zip).
3. Host the UI SDK on a web server that can be accessible for your application.
4. Create an HTML element and assign an HTML id. `<div id="syncloop"></div>`.
5. Call the `<BASE_PATH>/files/gui/middleware/pub/server/ui/workspace/syncloop.min.js` from the SDK.
6. Invoke the following javascript code to enable the editor on your HTML page.


```
$("#syncloop").syncloop({});
```

7. If you can see the API editor on your page, it means that it has been successfully enabled.
   To learn more about syncloop API building follow this [documentation](https://docs.syncloop.com/docs/user-guide/dashboard/workspace/services/api/api_page) or syncloop's [YouTube channel](https://www.youtube.com/@syncloop).

   ![APIEditor](https://docs.syncloop.com/assets/img/docs/sdk/editor.png)



<h2 id="Syncloop_embedded_integration_js_configurations"> </h2>

#### JS Configurations
Syncloop Embedded provides the following JS configuration:

```
{
            unique_id: <UUID>,
            test: true|false,
            export: true|false,
            import: true|false,
            documentation: true|false,
            save: true|false,
            save_callback: function(data) {
                //save data
            },
            load_service_callback: function () {
                return {...};
            },
            width: '100%',
            height: '100%',
            config: {
                service: {
                    list_options: {
                        embedded_services_label: "embedded",
                        embedded_services_context: "contexts",
                        embedded_services_function: "functions"
                    }
                }
            },
            service_base_url: <SERVER_RUNTIME_BASEPATH>
        }
```

1. `service_base_url`: Adds the [server runtime](https://docs.syncloop.com/docs/syncloop-embedded-integration/server-runtime) base path where all the Syncloop APIS are hosted.
2. `unique_id` provides the unique ID for this configuration. It is used for many internal purposes.
3. `test`: It requires **true** or **false** values to enable/disable it on the UI. Syncloop provides a test feature where you can test your service/rule on the UI itself.
4. `export`: It requires **true** or **false** values to enable/disable it on the UI. This option allows you to export the UI service/rule as a file.
5. `documentation`: The documentation link in the editor requires either **true** or **false** values to enable/disable it.
6. `save`: To enable/disable the save function on the UI, it requires **true** or **false** values.
7. `save_callback`: This is the callback function that triggers when you click the save button on the editor. You will have your editor's service in the form of JSON in the **data** variable. Later, you can save it where you want.
8. `load_service_callback`: This is a callback function that loads your saved/exported service programmatically on the editor.
9. `width': Defines the width of the editor.
10. `height': Defines the height of the editor.
11. `config`: Additional editor's configuration.
    i. `config.service.list_options`: This is the naming configuration for your [Local Java Function Integration](https://docs.syncloop.com/docs/syncloop-embedded-integration/local-java-function-integration).





### Integration Module


Syncloop offers a free library called Integration Module, which includes the most common Java & API services (***i.e. Date, String, IO***) that can be invoked in your [API services](https://docs.syncloop.com/docs/user-guide/control-structures/service) in Embedded Editor.

You can download the Syncloop integration module by clicking on this [link](https://www.syncloop.com/download-sdk.html).

Unzip the downloaded file (*integration-module.zip* from SDK zip file) in a location that can be accessed from the filesystem. This integration module is required for the [Server Runtime](https://docs.syncloop.com/docs/syncloop-embedded-integration/server-runtime).

Integration module will also include a service.properties file. Add Integration module path followed by /integration/middleware/


Example (server.properties):

`middleware.server.home.dir=/home/root/integration-module/integration/middleware/`



### Server Runtime


The Syncloop Embedded Server Runtime is a Java library that executes the Syncloop API created from the API Editor. This library can be added to any type of Java Maven project and will function as part of the existing project.


The developer needs to follow a few instructions to enable the Syncloop Embedded Runtime.

1. Open your pom.xml in your mvn project and follow the dependency in the repositories tag and dependencies tag, respectively.
```
<dependency>
    <groupId>com.syncloop.middleware</groupId>
   	<artifactId>core</artifactId>
   	<version>1.6.7</version>
</dependency>
```

2. Now create an object of `com.eka.middleware.sdk.Binder` class & pass the [Server.properties](https://docs.syncloop.com/docs/syncloop-embedded-integration/integration-module)'s file containing the directory path.
   Example: \
   `Binder binder = new Binder("/home/root/integration-module/");`

3. Invoke the `run()` method of the above object. `run()` method required 3 parameters
    1. **Unique ID**
    2. **Syncloop API's JSON**
    3. **API's Input Parameters**

```
Example:
public static void main(String[] args) throws Exception {
    Binder binder = new Binder("/home/root/integration-module/");;
    String serviceJson = "{...}";
    Map<String, Object> input = Maps.newHashMap();
       
    input.put("params1", "value1");
    input.put("params2", "value2");
        
    Map<String, Object> resp = binder.run(UUID.randomUUID().toString(), serviceJson, input);
       
    //response will contains out parameters which are mentioned in the API
}
```



### Integration & Embedded Services

Inside the [Integration Module](https://docs.syncloop.com/docs/syncloop-embedded-integration/integration-module) of the SDK that contains the most common Java & API services (***i.e. Date, String, IO***) can be invoked in [API services](https://docs.syncloop.com/docs/user-guide/control-structures/service) using Embedded Editor.


![Choose-a-Service](https://docs.syncloop.com/assets/img/docs/sdk/choose-a-service.png)

The above popup will be displayed once you right-click on the main panel & go to **Service > Others.** You can choose any service from the list to invoke API/Rule.

To enable the feature follow the following easy steps:

<h2 id="Syncloop_embedded_integration_service_list"> </h2>

### Service List

1. Create an endpoint with `/tenant/default/public/apiList2` & method should be `GET`.
2. Call `com.eka.middleware.sdk.Binder.getServices()` method and send out Map in the response from the service.
3. This is an example of the spring-boot application.
```
//Example:
@GetMapping("/tenant/default/public/apiList2")
public ResponseEntity<Map<String, Object>> getServices() {
   Binder binder = new Binder("/home/root/integration-module/");
   Map<String, Object> packages = binder.getServices();
   return new ResponseEntity<Map<String, Object>>(packages, HttpStatus.OK);
}
```

### Services from the Database
If you have services created from API editor and its json is saved in the database (*i.e. or in other place*) then you also can list out these services
inside **choose a service** popup under embedded package. call the following function of binder class to add the services.

1. To add service call `binder.addEmbeddedService(String serviceId, String serviceJson)`. This method required two parameters

   i. `serviceId`: Provide the name of the service, and it will show as the name of service.
   ii. `serviceJson`: Provide the JSON of the service.
   Example

```
//Example: 
@GetMapping("/tenant/default/public/apiList2")
public ResponseEntity<Map<String, Object>> getServices() {
   Binder binder = new Binder("/home/root/integration-module/");
        
   binder.addEmbeddedService('HTTPService', "{...}"); //Call always before binder.getServices();
   binder.addEmbeddedService('HTTPService2', "{...}");
        
   Map<String, Object> packages = binder.getServices();
   return new ResponseEntity<Map<String, Object>>(packages, HttpStatus.OK);
}
```
2. The below image is showing a service coming inside embedded package.

![Choose-a-Service](https://docs.syncloop.com/assets/img/docs/embedded-services-popup.png)

*Note: Make sure you called `binder.addEmbeddedService()` method always before `binder.getServices()` otherwise your services will not be shown in the popup*

### Service Detail

1. Create an endpoint with `/tenant/default/public/getServiceAsResponse` the method should be `GET`.
2. Call `com.eka.middleware.sdk.Binder.getServiceInfo(String location)` method and send out Map in the response from the service.

This is an example of the spring-boot application.
```
//Example:
@GetMapping("/tenant/default/public/getServiceAsResponse")
public ResponseEntity<Map<String, Object>> getServiceInfo(@RequestParam String location) {
    Binder binder = new Binder("/home/root/integration-module/");
    Map<String, Object> info = binder.getServiceInfo(location);
    return new ResponseEntity<Map<String, Object>>(info, HttpStatus.OK);
}
```
4. The output of this service will be used for the mapping Line View with the Service parameters.



### Local Java Function Integration

Syncloop offers to invoke your application's java function from the API/Rule & the function will work similarly as
how it works in your java application. Syncloop provides few easy APIs to integrate this feature.

Follow the following steps:

1. `@SyncloopFunction`: Add this annotation on the java function which you want to export and use in API/Rule &
   those class's function considered to be available in API/Rule editor. These are the following attributes of this
   annotation. This annotation is applicable on functions & constructors in the class.

   i. **in**: Mention the name of the parameters of the function in the same sequence.

   ii. **out**: Mention the name of the output parameter.

   iii. **title**: Put a title for the function.

   iv. **description**: Provide a description of the function.

Example

```
public class Example {
    @SyncloopFunction(in = {"orderId", "paymentId"}, title = "Payment Confirmation", description = "Confirm the order Payment", out = "status")
    public static Boolean paymentCapture(String orderId, String paymentId) throw PaymentException {
        ....
    }
    
    
    public static Boolean paymentRefund(String orderId, String paymentId) throw PaymentException { 
        ....
    }
}
```

2. `addFunctionClass()`: This function allows you to add your local Java function automatically to the syncloop service pool,
   which will later be available on the API/Rule editor.

   This function has following parameters: \
   i. **Class object**: Pass a java.lang.Class reference of a class. *e.g. object.getClass() or Example.class*. \
   ii. **allowNonSyncloopFunctions**: Pass a boolean value true/false. if you want to export only @SyncloopFunction's marked functions then pass it *false* or pass *true*.

Example:

```
Binder binder = new Binder("/home/root/integration-module/");
binder.addFunctionClass(Example.class, false) //It will automatically capture all syncloop functions from this class.
binder.addFunctionClass(Student.class, true) //It will automatically capture all syncloop functions from this class.
```

3. `addContextObject()`: This function allows you to add your java object into syncloop service pool, these objects will use to invoke non-static functions from
   API/Rule editor.

   This function has following parameters: \
   i. **objectName**: Name of the object which will be show on API/Rule editor. \
   ii. **object**: Object which you want to store.


3. **Function/List**: Implement the same API service list to get your function. If you have already implemented the `binder.getServices()` function, you don't have to do anything. The same function will fetch all types of services.

4.	Once you complete all the above steps, your java function will be loaded in the **Choose a Service** popup along with other services, as shown in the screenshot below.

### **Choose a Service** Popup View (Only `@SyncloopFunction` Enabled function)

![Choose-a-Service](https://docs.syncloop.com/assets/img/docs/sdk/choose-a-service-sl-m.png)

### API editor view after function choose & Mapping view

![Choose-a-Service](https://docs.syncloop.com/assets/img/docs/sdk/api-service-sl-m.png)

### **Choose a Service** Popup View (Non-Static functions)

![Choose-a-Service](https://docs.syncloop.com/assets/img/docs/sdk/choose-a-service-sl-m-ns.png)

### API editor view after function choose & Mapping view

![Choose-a-Service](https://docs.syncloop.com/assets/img/docs/sdk/api-service-sl-m-ns.png)

## Enable Test & Trace

The Syncloop Embedded SDK is a helpful tool for developers as it provides a way to test APIs on their platform. The SDK also has an interactive debugging feature that makes it easy for developers to navigate their service step-by-step and visualize all the input and output parameters at each step. The debugging process is implemented using Snapshots, which keep track of input and output variables at each step. These variables can be visually analyzed in the Syncloop workspace. The same features can also be used on the Syncloop Embedded SDK. Additionally, the SDK provides options for testing & tracing the API that has been created in the SDK environment.

---

[How to enable Snapshot/Tracing in API](https://docs.syncloop.com/docs/user-guide/dashboard/workspace/services/api/debugging#debugging_syncloop_enable_snapshot)



---

### JavaScript Integration

1. `enableEmbeddedTest()`: By enabling this function, developers can activate the test feature on the user interface of the API editor. This will allow developers to test and verify the functionality of their APIs in a safe and controlled environment, without affecting any live systems. It is a useful tool for ensuring that your API works as intended before releasing it to users. Learn [UI Editor Integration](https://docs.syncloop.com/docs/syncloop-embedded-integration/ui-editor-integration). Once the **Test** button is enabled it starts appearing on the top right. \
   Example
```
var iframe = document.getElementById("syncloop_iframe");
var iframeWindow = iframe.contentWindow;
iframeWindow.enableEmbeddedTest()
```
2. `enableEmbeddedTrace()`: While testing APIs using the 'Test your API' popup, the developers can use this function to enable API debugging. By doing so, the developers will be able to perform more comprehensive service checks and obtain more detailed information about the API's behavior during the testing process. This can be quite useful in identifying and resolving issues more quickly and efficiently than they would be able to otherwise. Read more about [Debugging Service](https://www.syncloop.com/developers.html)
   Example
```
var iframe = document.getElementById("syncloop_iframe");
var iframeWindow = iframe.contentWindow;
iframeWindow.enableEmbeddedTrace()
```

### Server Side Integration
To activate Test & Tracing, it is necessary to set up two services on the server side. To do this, there are a few steps that need to be followed. By completing these steps, the services will be enabled.

### JsonToSchema

This service will convert Syncloop embedded input to a JSON schema.

1. Create an endpoint with `/tenant/default/public/jsonToSchema` & Method should be POST & enable the request payload.
2. Call `com.eka.middleware.sdk.BinderUtils.convert(JSON)` and pass the JSON received from the request payload.
3. Return the output of the above function as a response.
4. This is an example of the spring-boot application.

```
@PostMapping("/tenant/default/public/jsonToSchema")
public ResponseEntity<Object> getServiceInfo(@RequestBody Map<String, String> payload) {
    Object schema = BinderUtils.convert(payload.get("json"));
    return new ResponseEntity<Object>(schema, HttpStatus.OK);
}
```

### executeApi

This service will execute the API/Rule.

1. Create an endpoint with `/tenant/default/public/executeApi` & Method should be set to POST &  the request payload should be enabled.
2. Follow the [Server Runtime](https://docs.syncloop.com/docs/syncloop-embedded-integration/server-runtime) to call the method.
3. Return the output of the above function as a response.
4. This is an example of the spring-boot application.

```
@PostMapping("/tenant/default/public/executeApi")
public ResponseEntity<Map<String, Object>> executeApi(@RequestBody Map<String, Object> payload) throws Exception {
   Binder binder = new Binder("/home/root/integration-module/");
   Map<String, Object> resp = binder.runAsync(UUID.randomUUID().toString(), payload.get("apiJson").toString(), (Map)payload.get("payload"));
   return new ResponseEntity<Map<String, Object>>(resp, HttpStatus.OK);
}
```
5. `binder.run()` is also available & use it when you want to execute api in same worker thread.

*Note: All internal functionalities are integrated, you have to enable & create the endpoint on your server. Make sure all endpoints are accessible to the UI editor.*
