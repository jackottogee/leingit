#!/usr/bin/env bb

(ns lein-git
	(:require [clojure.tools.cli :refer [parse-opts]]	
	          [babashka.tasks :refer [run shell]]
              [babashka.curl :as curl]
              [cheshire.core :as json]
              [clojure.java.io :as io]))

; lein
(defn lein-new 
    "Creates a new lein project"
    ([repo]
	 (shell (str "lein new " repo))))

(defn lein-repo-exists? 
    "Checks if a lein proect already exists"
    [repo]
	(.exists (io/file repo)))

(defn lein-delete
    "Deletes a lein project"
    [repo]
	(shell (str "rm -r " repo)))

; github
(def user-url "https://api.github.com/user/repos")
(def token (System/getenv "GIT_TOKEN"))
(def username (System/getenv "PROFILE"))
(println token)
(println username)
(def headers {"Accept" "application/json"
              "Authorization" (str "Bearer " token)})


(defn get-repos-request
    "Gets all repos for auth'ed github account"
	[]
	(curl/get user-url {:headers headers}))

(defn get-repos
    "Gets all repo names from github repo request"
	[]
	(let [resp (get-repos-request)]
		(map #(get % "name") (json/parse-string (:body resp)))))

(defn git-repo-exists?
    "Checks if a repo already exists and returns a boolean value on the case"
	[repo-name]
	(let [repos (get-repos)]
		(if (empty? (filter #(= repo-name %) repos))
			false
			true)))

(defn make-repo-request 
    "Makes a repo for auth'ed github account"
	[repo-name]
	(curl/post user-url {:headers headers
                        :body     (json/generate-string {"name" repo-name})
                        :throw    false}))

(defn make-repo
    "Makes a repo if it doesn't already exist on github"
	[repo-name]
	(let [repos (get-repos)]
		(if (empty? (filter #(= repo-name %) repos))
			(make-repo-request repo-name)
			(println "error"))))

(defn get-repo-url
    "Makes the url for deletion and cloning requests"
	[owner repo]
	(str "https://api.github.com/repos/" owner "/" repo))

(defn delete-repo-request
    "Deletes a repo using the clone repo url"
	[repo-name]
	(curl/delete (get-repo-url username repo-name) {:headers headers
                                                    :throw   false}))

(defn git-delete-repo
    "Deletes a repo if it exists"
	[repo-name]
	(let [repos (get-repos)]
		(if (empty? (filter #(= repo-name %) repos))
		(println "Repo doesn't exist")
		(delete-repo-request repo-name))))

(defn get-clone-repo-url
    "Finds the url for html cloning"
	[repo-name]
	(get (json/parse-string 
            (:body 
                (curl/get (get-repo-url username repo-name)) {:headers headers
                                                               :throw false})) 
        "clone_url"))

(defn clone-repo
    "Clones a git repo"
	[repo-name]
	(let [url (get-clone-repo-url repo-name)]
		(shell (str "git clone" url))))

(defn git-init-commands
    "A chain of git commands that initialise, add, commit, add origin, and push a file."
    [repo-name] 
    (do
		(shell {:dir repo-name} "git init")
     	(shell {:dir repo-name} "git add .")
     	(shell {:dir repo-name} "git commit -m \"first commit\"")
	 	(shell {:dir repo-name} "git branch main")
     	(shell {:dir repo-name} (str "git remote add origin " (get-clone-repo-url repo-name)))
     	(shell {:dir repo-name} "git remote -v")
     	(shell {:dir repo-name} "git push origin main")))

; leingit
(defn leingit-make-repo
    "Main function to make a lein project and github"
	[repo-name]
	(let [lein-exists (lein-repo-exists? repo-name)
		  git-exists  (git-repo-exists? repo-name)]
		(cond
			(and lein-exists git-exists) (
                                          ;; Project already exists both locally and in github, do nothing.
                                          println "Repo exists both locally and in github.")
			(= lein-exists true)         (do
                                            ;; Project already exists locally, so pushes it to git.
											(println "Repo exists locally. Pushing to git.")
                                            (make-repo repo-name)
                                            (git-init-commands repo-name)
                                            (println "Created git repo."))
			(= git-exists true)		     (do
                                            ;; Repo exits on github, pulls it down.
											(println "Repo exists in git. Cloning locally.")
											(shell (str "mkdir " repo-name))
                                            (shell (str "git clone " (get-clone-repo-url repo-name)))
                                            (println "Cloned git repo."))
				:else                    (do
                                            ;; Repo exists neither locally or on github, creates both!
											(println "Creating repo")
											(lein-new repo-name)
											(make-repo repo-name)
											(git-init-commands repo-name)
											(println "Created new repo.")))))
; opts
(def cli-options
	[["-n" "--name NAME" "Project name"]])

(def parsed-args (parse-opts *command-line-args* cli-options))

(let [options (:options parsed-args)]
    (if (contains? options :name)
        (leingit-make-repo (options :name)) (println "Project name not included")))