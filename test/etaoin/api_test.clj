(ns etaoin.api-test
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [try+]]
            [clojure.test :refer :all]
            [etaoin.api :refer :all])
  (:import javax.imageio.ImageIO))

(defmacro with-tmp-file [prefix suffix bind & body]
  `(let [tmp# (java.io.File/createTempFile ~prefix ~suffix)
         ~bind (.getAbsolutePath tmp#)]
     (try
       ~@body
       (finally
         (.delete tmp#)))))

(defn numeric? [val]
  (or (instance? Double val)
      (instance? Integer val)))

(def ^:dynamic *driver*)

(defn fixture-browsers [f]
  (let [url (-> "html/test.html" io/resource str)]
    (doseq [type [:firefox :chrome :phantom]]
      (with-driver type {} driver
        (go driver url)
        (wait-visible driver {:id :document-end})
        (binding [*driver* driver]
          (f))))))

(use-fixtures
  :each
  fixture-browsers)

(deftest test-visible
  (doto *driver*
    (-> (visible? {:id :button-visible}) is)
    (-> (invisible? {:id :button-hidden}) is)
    (-> (invisible? {:id :div-hidden}) is)
    (-> (invisible? {:id :dunno-foo-bar}) is)))

;; (deftest test-clear
;;   (let [form "//form[@id='submit-test']"
;;         input "//input[@id='simple-input']"
;;         submit "//input[@id='simple-submit']"]
;;     (testing "simple clear"
;;       (with-xpath
;;         (fill input "test")
;;         (clear input)
;;         (click submit)
;;         (let [url (get-url)]
;;           (is (str/ends-with? url "?login=&password=&message=")))))
;;     (testing "form clear"
;;       (with-xpath
;;         (fill-form form {:login "Ivan"
;;                          :password "lalilulelo"
;;                          :message "long_text_here"})
;;         (clear-form form)
;;         (click submit)
;;         (let [url (get-url)]
;;           (is (str/ends-with? url "?login=&password=&message=")))))))

(deftest test-enabled
  (doto *driver*
    (-> (disabled? {:id :input-disabled}) is)
    (-> (enabled? {:id :input-not-disabled}) is)
    (-> (disabled? {:id :textarea-disabled}) is))
  (is (thrown?
       clojure.lang.ExceptionInfo
       (enabled? *driver* {:id :dunno-foo-bar}))))

(deftest test-exists
  (doto *driver*
    (-> (exists? {:tag :html}) is)
    (-> (exists? {:tag :body}) is)
    (-> (absent? {:id :dunno-foo-bar}) is)))

(deftest test-alert
  (skip-phantom
   *driver*
   (doto *driver*
     (click {:id :button-alert})
     (-> get-alert-text (= "Hello!") is)
     (-> has-alert? is)
     (accept-alert)
     (-> has-alert? not is)
     (click {:id :button-alert})
     (-> has-alert? is)
     (dismiss-alert)
     (-> has-alert? not is))))

(deftest test-attributes
  (testing "common attributes"
    (doto *driver*
      (-> (get-element-attrs
           {:id :input-attr}
           :id :type :value :name :style
           "disabled" "data-foo" "data-bar")
          (= ["input-attr"
              "text"
              "hello"
              "test"
              "border: 5px; width: 150px;"
              "true"
              "foo"
              "bar"])
          is)))
  (testing "event attributes"
    (doto *driver*
      (-> (get-element-attr
           {:id :input-attr}
           :onclick)
          (= "alert(123)")
          is)))
  (testing "missing attributes"
    (doto *driver*
      (-> (get-element-attrs
           {:id :input-attr}
           :foo "bar" :baz "dunno")
          (= [nil nil nil nil])
          is))))

(deftest test-title
  (doto *driver*
    (-> get-title (= "Webdriver Test Document") is)))

(deftest test-url
  (doto *driver*
    (-> get-url
        (str/ends-with? "/resources/html/test.html")
        is)))

(deftest test-css-props
  (testing "single css"
    (doto *driver*
      (-> (get-element-css {:id :div-css-simple} :display)
          (= "block")
          is)))
  (testing "multiple css"
    (let [result (get-element-csss
                  *driver*
                  {:id :div-css-simple}
                  :display :background-color "width" "height")
          [display background-color width height] result]
      (is (= display "block"))
      (is (or (= background-color "rgb(204, 204, 204)")
              (= background-color "rgba(204, 204, 204, 1)")))
      (is (= width "150px"))
      (is (= height "250px"))))
  (testing "styled css"
    (let [result (get-element-csss
                  *driver*
                  {:id :div-css-styled}
                  :display :width :height)
          [display width height] result]
      (is (= display "block"))
      (is (= width "333px"))
      (is (= height "111px"))))
  (testing "missing css"
    (let [result (get-element-csss
                  *driver*
                  {:id :div-css-styled}
                  :foo :bar "baz")]
      (is (every? nil? result)))))

(deftest test-wait-text
  (testing "wait for text simple"
    (doto *driver*
      (refresh)
      (click {:id :wait-button})
      (wait-has-text "-secret-"))
    (is true "text found"))
  (testing "wait for text timeout"
    (doto *driver*
      (refresh)
      (click {:id :wait-button}))
    (try+
     (wait-has-text *driver*
                    "-secret-"
                    {:timeout 0.5
                     :message "No -secret- text on the page"})
     (is false "should not be executd")
     (catch [:type :etaoin/timeout] data
       (is (= (-> data (dissoc :predicate :time-rest))
              {:type :etaoin/timeout
               :message "No -secret- text on the page"
               :timeout 0.5
               :interval 0.1
               :times 6})))))
  (testing "wait for non-existing text"
    (refresh *driver*)
    (try+
     (wait-has-text *driver*
                    "-dunno-whatever-foo-bar-"
                    {:timeout 1})
     (is false "should not be executed")
     (catch [:type :etaoin/timeout] data
       (is (= (-> data (dissoc :predicate :time-rest))
              {:type :etaoin/timeout
               :message nil
               :timeout 1
               :interval 0.1
               :times 11}))))))

(deftest test-wait-has-class
  (is 1)
  (testing "wait for an element has class"
    (doto *driver*
      (refresh)
      (click {:id :wait-add-class-trigger})
      (wait-has-class {:id :wait-add-class-target} :new-one))))

(deftest test-close-window
  (is 1)
  (doto *driver*
    (close-window)))

(deftest test-drag-n-drop
  (let [url "http://marcojakob.github.io/dart-dnd/basic/web/"
        doc {:class :document}
        trash {:xpath "//div[contains(@class, 'trash')]"}]
    (skip-firefox
     *driver*
     (doto *driver*
       (go url)
       (drag-and-drop doc trash)
       (drag-and-drop doc trash)
       (drag-and-drop doc trash)
       (drag-and-drop doc trash)
       (-> (absent? doc)
           is)))))

(deftest test-element-location
  (let [q {:id :el-location-input}
        loc (get-element-location *driver* q)
        {:keys [x y]} loc]
    (is (numeric? x))
    (is (numeric? y))))

(deftest test-window-position
  (let [{:keys [x y]} (get-window-position *driver*)]
    (is (numeric? x))
    (is (numeric? y))
    (set-window-position *driver* (+ x 10) (+ y 10))
    (let [{:keys [x' y']} (get-window-position *driver*)]
      (is (not= x x'))
      (is (not= y y')))))

(deftest test-window-size
  (testing "getting size"
    (let [{:keys [width height]} (get-window-size *driver*)]
      (is (numeric? width))
      (is (numeric? height))
      (set-window-size *driver* (+ width 10) (+ height 10))
      (let [{:keys [width' height']} (get-window-size *driver*)]
        (not= width width')
        (not= height height')))))

(deftest test-active-element
  (testing "active element"
    (doto *driver*
      (click {:id :set-active-el})
      (-> (get-element-attr :active :id)
          (= "active-el-input")
          is))))

(deftest test-element-text
  (let [text (get-element-text *driver* {:id :element-text})]
    (is (= text "Element text goes here."))))

;; (deftest test-cookies
;;   (testing "getting all cookies"
;;     (with-cookies cookies
;;       (when-chrome
;;           (is (= cookies [])))
;;       (when-firefox
;;           (is (= cookies [{:name "cookie1",
;;                            :value "test1",
;;                            :path "/",
;;                            :domain "",
;;                            :expiry nil,
;;                            :secure false,
;;                            :httpOnly false}
;;                           {:name "cookie2",
;;                            :value "test2",
;;                            :path "/",
;;                            :domain "",
;;                            :expiry nil,
;;                            :secure false,
;;                            :httpOnly false}])))
;;       (when-phantom
;;           (is (= cookies [{:domain "",
;;                            :httponly false,
;;                            :name "cookie2",
;;                            :path "/",
;;                            :secure false,
;;                            :value "test2"}
;;                           {:domain "",
;;                            :httponly false,
;;                            :name "cookie1",
;;                            :path "/",
;;                            :secure false,
;;                            :value "test1"}])))))
;;   (testing "getting named cookie"
;;     (with-named-cookies "cookie2" cookies
;;       (when-chrome
;;           (is (= cookies [])))
;;       (when-firefox
;;           (is (= cookies [{:name "cookie2"
;;                            :value "test2"
;;                            :path "/"
;;                            :domain ""
;;                            :expiry nil
;;                            :secure false
;;                            :httpOnly false}])))
;;       (when-phantom
;;           (is (= cookies
;;                  [{:domain ""
;;                    :httponly false
;;                    :name "cookie2"
;;                    :path "/"
;;                    :secure false
;;                    :value "test2"}])))))
;;   (testing "setting a cookie"
;;     (skip-phantom
;;      (set-cookie {:httponly false
;;                   :name "cookie3"
;;                   :domain ""
;;                   :secure false
;;                   :value "test3"})
;;      (with-named-cookies "cookie3" cookies
;;        (when-firefox
;;            (is (= cookies
;;                   [{:name "cookie3"
;;                     :value "test3"
;;                     :path ""
;;                     :domain ""
;;                     :expiry nil
;;                     :secure false
;;                     :httpOnly false}]))))))

;;   (testing "deleting a named cookie"
;;     (skip-phantom
;;      (set-cookie {:httponly false
;;                   :name "cookie3"
;;                   :domain ""
;;                   :secure false
;;                   :value "test3"})
;;      (with-named-cookies "cookie3" cookies
;;        (when-firefox
;;            (is (= cookies
;;                   [{:name "cookie3"
;;                     :value "test3"
;;                     :path ""
;;                     :domain ""
;;                     :expiry nil
;;                     :secure false
;;                     :httpOnly false}]))))))
;;   (testing "deleting a named cookie"
;;     (delete-cookie "cookie3")
;;     (with-named-cookies "cookie3" cookies
;;       (is (= cookies []))))
;;   (testing "deleting all cookies"
;;     (delete-cookies)
;;     (with-cookies cookies
;;       (is (= cookies [])))))

(deftest test-page-source
  (let [src (get-source *driver*)]
    (when-firefox *driver*
      (is (str/starts-with? src "<html><head>")))
    (skip-firefox *driver*
     (is (str/starts-with? src "<!DOCTYPE html>")))))

(deftest test-screenshot
  (with-tmp-file "screenshot" ".png" path
    (screenshot *driver* path)
    (-> path
        io/file
        ImageIO/read
        is)))

(deftest test-js-execute
  (testing "simple result"
    (let [result (js-execute *driver* "return 42;")]
      (is (= result 42))))
  (testing "with args"
    (let [script "return {foo: arguments[0], bar: arguments[1]};"
          result (js-execute *driver* script {:test 42} [true, nil, "Hello"])]
      (is (= result
             {:foo {:test 42}
              :bar [true nil "Hello"]})))))

(deftest test-add-script
  (let [js-url (-> "js/inject.js" io/resource str)]
    (testing "adding a script"
      (add-script *driver* js-url)
      (let [result (js-execute *driver* "return injected_func();")]
        (is (= result "I was injected"))))))

(deftest test-set-hash
  (testing "set hash"
    (doto *driver*
      (set-hash "hello")
      (-> get-hash (= "hello") is)
      (-> get-url (str/ends-with? "/test.html#hello") is)
      (set-hash "goodbye")
      (-> get-url (str/ends-with? "/test.html#goodbye") is))))