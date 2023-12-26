(ns main
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]))

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))

(def created (partial response 201))

(def accepted (partial response 202))

(def echo {:name :echo
           :enter #(assoc % :response (ok (:request %)))})

(defonce database (atom {}))

(def db-interceptor
  {:name :db-interceptor
   :enter #(update % :request assoc :database @database)
 	 :leave (fn [ctx]
            (if-let [[operation & args] (:tx-data ctx)]
              (do
                (apply swap! database operation args)
                (assoc-in ctx [:request :database] @database))
              ctx))})

(defn make-list [nm] {:name nm :items {}})

(defn make-list-item [nm] {:name nm :done? false})

(def list-create-interceptor
  {:name :list-create
  	:enter (fn [ctx]
            (let [nm (get-in ctx [:request :query-params :name] "Unnamed List")
                  new-list (make-list nm)
                  db-id (str (gensym "1"))
                  url (route/url-for :list-view :params {:list-id db-id})]
              (assoc ctx
                     :response (created new-list "Location" url)
                     :tx-data [assoc db-id new-list])))})



(def routes
  (route/expand-routes
    #{["/todo" :post [db-interceptor list-create-interceptor]]
      ["/todo" :get echo :route-name :list-query-form]
      ["/todo/:list-id" :get echo :route-name :list-view]
      ["/todo/:list-id" :post echo :route-name :list-item-create]
      ["/todo/:list-id/:item-id" :get echo :route-name :list-item-view]
      ["/todo/:list-id/:item-id" :put echo :route-name :list-item-update]
      ["/todo/:list-id/:item-id" :delete echo :route-name :list-item-delete]}))


(def service-map
  {::http/routes routes
   ::http/type :jetty
   ::http/port 8890})

(defn start []
  (-> service-map http/create-server http/start))

;; For interactive development
(defonce server (atom nil))

(defn start-dev []
  (reset! server (-> (assoc service-map ::http/join? false)
                     http/create-server
                     http/start)))

(defn stop-dev []
  (when @server (http/stop @server)))

(defn restart []
  (stop-dev)
 	(start-dev))

(defn test-request [verb url]
  (test/response-for (::http/service-fn @server) verb url))

(comment
   @database
  (restart)
  (test-request :post "/todo?name=B-List")
  )