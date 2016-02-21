'use strict';
//FIXME: interceptor mechanism to detect invalid tokens and gracefully reroute to login page again
angular.module('mx.services', ['mx.config', 'ngResource'])
  // ----------------------------------------------------------------
  //  Auth
  // ----------------------------------------------------------------
  .factory('AuthSvc', ['$rootScope', '$resource', 'API_SERVER_ROOT', function($rootScope, $resource, API_SERVER_ROOT) {
     return $resource(API_SERVER_ROOT + '/auth', {}, {
       post: {method: 'POST', timeout: 5000},
       validateSession: {method: 'GET', url: API_SERVER_ROOT + '/auth/:authToken', timeout: 5000}, //TODO: put session in postbody
     }); 
   }])
  // ----------------------------------------------------------------
  //  Folders
  // ----------------------------------------------------------------
  .factory('FoldersSvc', ['$rootScope', '$resource', '$routeParams', 'API_SERVER_ROOT', function($rootScope, $resource, $routeParams, API_SERVER_ROOT) {
    var resource = $resource(API_SERVER_ROOT + '/folder', {}, {
       getMails: {method: 'GET', url: API_SERVER_ROOT + '/folder/:id', headers: {'X-MX-AuthToken' : $rootScope.authToken}, isArray: true, timeout: 5000},
       getFolders: {method: 'GET', headers: {'X-MX-AuthToken' : $rootScope.authToken}, isArray: true, timeout: 5000}
     });

    var svc = {
        _folders: null,
        _currentFolder: 'INBOX',
        getMails: resource.getMails, 
        getFolders: function() {
          if (!svc._folders) {
            // console.log("# Requesting to server for folders");
            var promise = resource.getFolders().$promise;
            promise.then(
                function(listing) {
                  svc._folders = listing;
                },
                function () {
                  // TODO: error
                  $rootScope.logout();
                }
            );
            return promise;
          } else {
            // console.log("# Returning folders from cache");
            return svc._folders;
          }
        },
        getCurrentFolder: function() { // can be made a property
          svc._currentFolder = $routeParams.folderName || svc._currentFolder; // if came from url
          return svc._currentFolder;
        }
    }

    return svc;
   }])
  // ----------------------------------------------------------------
  //  Mail
  // ----------------------------------------------------------------
  .factory('MailSvc', ['$rootScope', '$resource', 'API_SERVER_ROOT', function($rootScope, $resource, API_SERVER_ROOT) {
     return $resource(API_SERVER_ROOT + '/mail/:id', {id: '@id'}, {
       get: {method: 'GET', headers: {'X-MX-AuthToken' : $rootScope.authToken}, timeout: 5000},
       update: {method: 'PUT', headers: {'X-MX-AuthToken' : $rootScope.authToken}, timeout: 5000},
       delete: {method: 'DELETE', headers: {'X-MX-AuthToken' : $rootScope.authToken}, timeout: 5000}
     });
   }])
  // ----------------------------------------------------------------
  //  Mails
  // ----------------------------------------------------------------
  .factory('MailsSvc', ['$rootScope', '$resource', '$http', 'API_SERVER_ROOT', function($rootScope, $resource, $http, API_SERVER_ROOT) {
     var url = API_SERVER_ROOT + '/mail';
     var svc = {
       post: function(data) { return $http({method: 'POST', url: url, headers: {'X-MX-AuthToken' : $rootScope.authToken}, data: data, timeout: 5000}); },
       update: function(data) { return $http({method: 'PUT', url: url, headers: {'X-MX-AuthToken' : $rootScope.authToken}, data: data, timeout: 5000}); },
       delete: function(data) { return $http({method: 'DELETE', url: url, headers: {'X-MX-AuthToken' : $rootScope.authToken}, data: data, timeout: 5000}); },
     }

     return svc;

     // return $resource(API_SERVER_ROOT + '/mail', {}, {
     //   post: {method: 'POST', headers: {'X-MX-AuthToken' : $rootScope.authToken}, timeout: 5000},
     //   update: {method: 'PUT', headers: {'X-MX-AuthToken' : $rootScope.authToken}, isArray: true, timeout: 5000},
     //   kill: {method: 'DELETE', headers: {'X-MX-AuthToken' : $rootScope.authToken}, isArray: true, timeout: 5000}
     // });
   }]);
