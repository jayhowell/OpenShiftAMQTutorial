# Configuring and Using AMQ on OpenShift 4.x(4.11)

Description: This Doc will walk you through

* deploying the AMQ Operator on Openshift 4.x
* configuring the broker for different protocols
* generating self signed certs to use for your clients

### **Steps**

1. Go to Operators and chose the operator\<insert operator>
2. Create AMQ server from Broker
3. Create a self signed cert using the following commands in RHEL

    ```
    keytool -genkey -alias broker -keyalg RSA -keystore ./broker.ks
    keytool -export -alias broker -keystore ~/broker.ks -file ~/broker_cert.pem
    keytool -import -alias broker -keystore ~/client.ts -file ~/broker_cert.pem
    ```
4. Create the secret
    `oc create secret generic amq7brokersecret --from-file=broker.ks=/home/jhowell/broker.ks --from-file=client.ts=/home/jhowell/broker.ks --from-literal=keyStorePassword=password --from-literal=trustStorePassword=password`
5. Link the secret to the **BROKERS** Service Account. The broker operator service account will will make sure that a volume is mounted credential in the pods under /etc/\<secret-name-from-aboe>-volume or in this case /etc/amq7brokersecret-volume
    1. 7.10 and below
        `oc secrets link sa/amq-broker-operator secret/amq7brokersecret`
    2. 7.11 and above
        `oc secrets link sa/amq-broker-operator secret/amq7brokersecret<FIX THIS - the name changed in the 7.11 operator>`
6. 

```
apiVersion: broker.amq.io/v1beta1
kind: ActiveMQArtemis
metadata:
  name: ex-aao
  namespace: amq-demo
spec:
  acceptors:
    - name: amqp
      needClientAuth: false
      port: 5672
      protocols: core, amqp
      sslEnabled: true
      sslSecret: amq7brokersecret
      verifyHost: false
      expose: true
  console:
    expose: true
  adminUser: admin
  adminPassword: password
```

* The acceptor in the spec defines how the broker will accept messages
    * configures an acceptor on port 5672
    * protocols core and amqp please see definitions in docs\<insert link here>
    * `sslEnabled` and `sslSecret` work together.
    * `verifyHost` is important. In the CN of your cert, you can specify a domain name like mydomain.com. If verify host is set to true, it will verify that the host has this domain name.
    * `expose:true` lets us know that it will create a route automatically for you. All routes require SSL. You can create your own route to port 61616 if you like with no ssl in it.
    * console:expose:true is important becuase this allows you to get to the admin console for the broker.