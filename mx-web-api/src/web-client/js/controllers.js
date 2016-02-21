'use strict';

//FIXME:
// - update flgs when moving in/out of trash

angular.module('mx.controllers', [])
  // ----------------------------------------------------------------
  //  Login
  // ----------------------------------------------------------------
  .controller('LoginController', ['$cookies', '$rootScope', '$scope', '$location', '$routeParams', 'AuthSvc', function($cookies, $rootScope, $scope, $location, $routeParams, AuthSvc) {
    $scope.authenticate = function() {
      AuthSvc.post({email: $scope.email, password: $scope.password},
        function(resp) {
          $cookies.authToken = resp.token;
          $rootScope.authToken = resp.token;
          $location.path('/folder');
        },
        function(error) {
          console.log(error);
          $scope.error = 'Invalid email or password';
        })
    };
  }])
  // ----------------------------------------------------------------
  //  Folder Mails
  // ----------------------------------------------------------------
  .controller('FolderController', ['$rootScope', '$scope', '$location', '$routeParams', '$q', 'FoldersSvc', 'MailsSvc', function($rootScope, $scope, $location, $routeParams, $q, FoldersSvc, MailsSvc, foldersDep) {
    $scope.checked = {};
    $scope.folders = FoldersSvc.getFolders();
    $scope.currentFolder = FoldersSvc.getCurrentFolder();
    $scope.listing = true; // initial loading

    var getChecked = function() {
      return _.filter(_.keys($scope.checked), function(k) { return $scope.checked[k]; });
    };
    var getMails = function() {
      var deferred = $q.defer();
      $scope.error = false;

      FoldersSvc.getMails({id: $scope.currentFolder},
        function(listing) {
          deferred.resolve(listing);
        },
        function(error) {
          $scope.error = 'HTTP ' + error.status;
          deferred.reject('');
          $rootScope.logout();
        }
      );
      return deferred.promise;
    };

    $scope.movableFolders = function() {
      return _.filter($scope.folders, function(folder) {
        return folder.name != 'SENT' && folder.name != $scope.currentFolder;
      });
    };
    $scope.toggleCheckbox = function(evt, mailId) {
      var isChecked = $scope.checked[mailId];
      $scope.checked[mailId] = !isChecked; 
      evt.stopPropagation();
    };
    $scope.hasChecked = function() {
      return _.some(_.values($scope.checked));
    };
    $scope.isChecked = function(mailId) {
      return $scope.checked[mailId];
    };
    $scope.refresh = function() {
      getMails().then(
        function(listing) {
          $scope.listing = listing;
        },
        function(rejectedResponse) {
        }
      );
    };
    $scope.read = function(mailId) {
      $location.path('/read/' + mailId);
    };
    $scope.flagSeen = function(seen) {
      var flagReq = _.reduce(getChecked(), function(req, mailId) {
        req.push({mailId: parseInt(mailId), flgSeen: seen});
        return req;
      }, []);

      MailsSvc.update(flagReq).then(function() {
        $scope.refresh();
      });
    };
    $scope.moveToFolder = function(folderName) {
      var moveReq = _.reduce(getChecked(), function(req, mailId) {
        req.push({mailId: parseInt(mailId), folder: folderName});
        return req;
      }, []);

      MailsSvc.update(moveReq).then(function() {
        $scope.refresh();
      });
    };
    $scope.deleteChecked = function() {
      MailsSvc.delete(getChecked()).success(function() {
        $scope.refresh();
      });
    }
  }])
  // ----------------------------------------------------------------
  //  Read
  // ----------------------------------------------------------------
  .controller('ReadController', ['$scope', '$location', '$routeParams', 'FoldersSvc', 'MailSvc', function($scope, $location, $routeParams, FoldersSvc, MailSvc) {
    $scope.folders = FoldersSvc.getFolders();
    $scope.currentFolder = FoldersSvc.getCurrentFolder();

    $scope.getMail = function(fmt) {
      $scope.mail = MailSvc.get({id: $routeParams.mailId, fmt: fmt},
        function(mail) {
          $scope.error = null;
          $scope.ready = true;
          var mailFolder = _.find($scope.folders, function(f) {
            return f.id == mail.folderId;
          });
          $scope.mailFolder = mailFolder.name;
          $scope.currentFolder = mailFolder.name;
        },
        function(error) {
          $scope.error = "HTTP " + error.status;
        }
      );
    };
    $scope.movableFolders = function() {
      return _.filter($scope.folders, function(folder) {
        return (folder.name != 'SENT') && (folder.name != $scope.mailFolder);
      });
    }
    $scope.moveToFolder = function(folder) {
      MailSvc.update({id: $routeParams.mailId}, {folder: folder, flgDeleted: folder == 'TRASH'}, function() {
        $location.path('/folder');
      });
    };
    $scope.reply = function() {
      $location.search('reply', $routeParams.mailId).path("/compose");
    };
    $scope.delete = function() {
      MailSvc.delete({id: $scope.mail.id}, function() {
        $location.path('/folder');
      });
    }
  }])
  // ----------------------------------------------------------------
  //  Compose
  // ----------------------------------------------------------------
  .controller('ComposeController', ['$scope', '$location', '$routeParams', 'FoldersSvc', 'MailSvc', 'MailsSvc', function($scope, $location, $routeParams, FoldersSvc, MailSvc, MailsSvc) {
    $scope.folders = FoldersSvc.getFolders();
    $scope.currentFolder = FoldersSvc.getCurrentFolder();
    $scope.error = false;

    $scope.send = function() {
      if (!$scope.compose.to && !$scope.compose.cc && !$scope.compose.bcc) {
        $scope.error = "Recipient required";
        return;
      }

      var compose = $scope.compose
      var post = MailsSvc.post({
        subject: compose.subject,
        to: compose.to ? compose.to.split(',') : null,
        cc: compose.cc ? compose.cc.split(',') : null,
        bcc: compose.bcc ? compose.bcc.split(',') : null,
        data: compose.data,
      });
      post.success(function(response) {
        $location.path('/folder');
      });
      post.error(function(data, status, headers, config) {
        $scope.error = "Server Response: HTTP " + data.description;
      });
    };
    // Reply
    if ($routeParams.reply) {
      // Assuming for now that mail has text content
      MailSvc.get({id: $routeParams.reply, fmt: 'text'}, function(mail) {
        $scope.compose = {
          subject: 'RE: ' + mail.subject,
          to: mail.from.address,
          data: '\r\n\r\n' + _.map(mail.data.split('\r\n'), function(line) { return '> ' + line; }).join('\r\n')
        };
      })
    }
    // New
    else
    {
      $scope.compose = {
        subject: '',
        to: '',
        cc: '',
        bcc: '',
        data: ''
      }
    }
  }]);
