'use strict';

angular.module('mx', ['ngRoute', 'ngCookies', 'mx.filters', 'mx.services', 'mx.directives', 'mx.controllers'])
  .config(['$routeProvider', function($routeProvider) {
    var dependencies = { // to delay loading
        foldersDep: function(FoldersSvc) {
          return FoldersSvc.getFolders();
        }
    }

    $routeProvider.when('/login', {
        templateUrl: 'partials/login.html',
        controller: 'LoginController'});

    $routeProvider.when('/folder', {
        templateUrl: 'partials/folder.html',
        controller: 'FolderController',
        resolve: dependencies});

    $routeProvider.when('/folder/:folderName', {
        templateUrl: 'partials/folder.html',
        controller: 'FolderController',
        resolve: dependencies});

    $routeProvider.when('/compose', {
        templateUrl: 'partials/compose.html',
        controller: 'ComposeController',
        resolve: dependencies});

    $routeProvider.when('/read/:mailId', {
        templateUrl: 'partials/read.html',
        controller: 'ReadController',
        resolve: dependencies});

    $routeProvider.otherwise({redirectTo: '/login'});
  }])
  .run(['$cookies', '$rootScope', '$window', '$location', 'AuthSvc', function($cookies, $rootScope, $window, $location, AuthSvc) {
    $rootScope.initialize = function() {
      $rootScope.currentFolder = 'INBOX';

      if ($cookies.authToken) {
        // AuthSvc.validateSession({authToken: $cookies.authToken}, //TODO: this does not block
        //   function(response) {
        //     // Valid authToken
        //     $rootScope.initialized = true;
        //   },
        //   function() {
        //     // Invalid authToken
        //   }
        // );
        $rootScope.authToken = $cookies.authToken;
      } else {
        $rootScope.authToken = null;
        $location.path('/login'); //.search('to=', $location.path());
      }
    }

    $rootScope.logout = function() {
      $rootScope.authToken = null;
      delete $cookies.authToken;
      // $location.path('/login');
      $window.location.reload();
      //FIXME: tear down cached folders, sessions, etc
  	}

    $rootScope.initialize();
  }]);
  
angular.module('mx.config', [])
  .constant('API_SERVER_ROOT', 'http://localhost:8080');
