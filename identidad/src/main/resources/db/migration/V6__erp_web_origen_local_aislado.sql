UPDATE oauth2_registered_client
SET redirect_uris='http://127.0.0.1:8089/login/oauth2/code/boston-erp',
    post_logout_redirect_uris='http://127.0.0.1:8089/'
WHERE client_id='erp-web';
