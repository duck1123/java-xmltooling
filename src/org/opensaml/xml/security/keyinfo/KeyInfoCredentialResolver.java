/*
 * Copyright [2007] [University Corporation for Advanced Internet Development, Inc.]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opensaml.xml.security.keyinfo;

import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensaml.xml.XMLObject;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.credential.AbstractCredentialResolver;
import org.opensaml.xml.security.credential.BasicCredential;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.credential.CredentialCriteriaSet;
import org.opensaml.xml.security.credential.CredentialResolver;
import org.opensaml.xml.security.keyinfo.provider.DSAKeyValueProvider;
import org.opensaml.xml.security.keyinfo.provider.RSAKeyValueProvider;
import org.opensaml.xml.security.keyinfo.provider.X509DataProvider;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.KeyInfoHelper;
import org.opensaml.xml.signature.KeyName;
import org.opensaml.xml.signature.KeyValue;

/**
 * Specialized credential resolver interface which resolves credentials based on a {@link KeyInfo}  element.
 */
public class KeyInfoCredentialResolver extends AbstractCredentialResolver<KeyInfoCredentialContext>
    implements CredentialResolver<KeyInfoCredentialContext> {

    /** The default credential context class to use in resolved credentials.  */
    public static final Class<KeyInfoCredentialContext> DEFAULT_CONTEXT_CLASS = KeyInfoCredentialContext.class;
    
    /** List of KeyInfo providers that are registered on this instance.  */
    private List<KeyInfoProvider> providers;
    
    /** Constructor. */
    public KeyInfoCredentialResolver() {
        setContextClass(DEFAULT_CONTEXT_CLASS);
        providers = new ArrayList<KeyInfoProvider>();
        
        //TODO decide how/where providers will be registered
        // - programatically and/or declaratively
        // - global vs. instance local distinction
        
        //TODO TEMP just to do some testing 
        providers.add(new RSAKeyValueProvider());
        providers.add(new DSAKeyValueProvider());
        providers.add(new X509DataProvider());
    }

    /** {@inheritDoc} */
    public Iterable<Credential> resolveCredentials(CredentialCriteriaSet criteriaSet) throws SecurityException {
        KeyInfoCredentialCriteria kiCriteria = criteriaSet.getCriteria(KeyInfoCredentialCriteria.class);
        if (kiCriteria == null) {
            throw new SecurityException("Credential criteria set did not contain an instance of" 
                    + "KeyInfoCredentialCriteria");
        }
        KeyInfo keyInfo = kiCriteria.getKeyInfo();
        
        // This will be the list of credentials to return.
        List<Credential> credentials = new ArrayList<Credential>();
 
        KeyInfoResolutionContext kiContext = new KeyInfoResolutionContext(credentials);
        
        // Note: we allow KeyInfo to be null to handle case where application context, 
        // other accompanying criteria, etc, should be used to resolve credentials via hooks below.
        if (keyInfo != null) {
            // Initialize the resolution context that will be used by the provider plugins.
            // This processes the KeyName and the KeyValue children, if either are present.
            initResolutionContext(kiContext, keyInfo, criteriaSet);
            
            // Now process all other non-KeyName  and non-KeyValue children
            processKeyInfoChildren(kiContext, keyInfo, criteriaSet, credentials);
            
            if (credentials.isEmpty()) {
                // Add the credential based on plain KeyValue if no more specifc cred type was found
                if (kiContext.getKeyValueCredential() != null) {
                    credentials.add(kiContext.getKeyValueCredential());
                }
            }
        }
        
        // Postprocessing hook
        postProcess(kiContext, keyInfo, criteriaSet, credentials);
        
        // Final empty credential hook
        if (credentials.isEmpty()) {
            postProcessEmptyCredentials(kiContext, keyInfo, criteriaSet, credentials);
        }
        
        return credentials;
    }

    /**
     * Hook for subclasses to do post-processing of the credential set after all KeyInfo children have been processed.
     * 
     * For example, the previoulsy resolved credentials might be used to index into a store
     * of local credentials, where the index is a key name or the public half of a key pair
     * extracted from the KeyInfo.
     * 
     * @param kiContext KeyInfo resolution context containing 
     * @param keyInfo the KeyInfo to evaluate
     * @param criteriaSet the credential criteria used to resolve credentials
     * @param credentials the list which will store the resolved credentials
     * @throws SecurityException thrown if there is an error during processing
     */
    protected void postProcess(KeyInfoResolutionContext kiContext, KeyInfo keyInfo, 
            CredentialCriteriaSet criteriaSet, List<Credential> credentials) throws SecurityException { }

    /**
     * Hook for processing the case where no credentials were returned by any resolution method
     * by any provider, nor by the processing of the postProcess() hook.
     * 
     * @param kiContext KeyInfo resolution context containing 
     * @param keyInfo the KeyInfo to evaluate
     * @param criteriaSet the credential criteria used to resolve credentials
     * @param credentials the list which will store the resolved credentials
     * 
     * @throws SecurityException thrown if there is an error during processing
     */
    protected void postProcessEmptyCredentials(KeyInfoResolutionContext kiContext, KeyInfo keyInfo, 
            CredentialCriteriaSet criteriaSet, List<Credential> credentials) throws SecurityException {
        /* TODO probably move this specific code up to a SAML specific subclass 
         *      - are there other non-SAML PKIX use cases for name-only creds ?
        // Return a credential consisting only of key names - e.g. the SAML MD PKIX use case
        if (kiContext.getKeyNames() != null & ! kiContext.getKeyNames().isEmpty()) {
           credentials.add( buildKeyNameOnlyCredential(kiContext.getKeyNames(), keyInfo) );
        }
        */
    }

    /**
     * Use registered providers to process the non-KeyValue and non-KeyName children of KeyInfo.
     * 
     * @param kiContext KeyInfo resolution context containing 
     * @param keyInfo the KeyInfo to evaluate
     * @param criteriaSet the credential criteria used to resolve credentials
     * @param credentials the list which will store the resolved credentials
     * @throws SecurityException thrown if there is a provider error processing the KeyInfo children
     */
    private void processKeyInfoChildren(KeyInfoResolutionContext kiContext, KeyInfo keyInfo, 
            CredentialCriteriaSet criteriaSet, List<Credential> credentials) throws SecurityException {
        
        for (XMLObject keyInfoChild : keyInfo.getXMLObjects()) {
            if (keyInfoChild instanceof KeyName || keyInfoChild instanceof KeyValue) {
                continue;
            }
            for (KeyInfoProvider provider : providers) {
                Collection<Credential> creds = provider.process(this, keyInfoChild, criteriaSet, kiContext);
                if (creds != null && ! creds.isEmpty()) {
                    credentials.addAll(creds);
                    continue;
                }
            }
        }
    }
    
    /**
     * Initialize the resolution context that will be used by the providers.
     * 
     * @param kiContext KeyInfo resolution context containing 
     * @param keyInfo the KeyInfo to evaluate
     * @param criteriaSet the credential criteria used to resolve credentials
     * @throws SecurityException thrown if there is an error processing the KeyValue children
     */
    private void initResolutionContext(KeyInfoResolutionContext kiContext, KeyInfo keyInfo, 
            CredentialCriteriaSet criteriaSet) throws SecurityException {
        // Extract all KeyNames
        kiContext.setKeyNames(KeyInfoHelper.getKeyNames(keyInfo));
        // Extract the Credential based on the (singular) key from an existing KeyValue(s).
        resolveKeyValue(kiContext, keyInfo.getKeyValues(), criteriaSet);
    }

    /**
     * Resolve the key from any KeyValue element that may be present.
     * 
     * Note: this assumes that KeyInfo/KeyValue will not be abused via-a-vis the Signature specificiation,
     * and that therefore all KeyValue elements (if there is even more than one) will all resolve to the same
     * key value. Therefore, only the first credential derived from a KeyValue will be be utilized.
     * 
     * @param kiContext KeyInfo resolution context containing 
     * @param keyValues the KeyValue children to evaluate
     * @param criteriaSet the credential criteria used to resolve credentials
     * 
     * @throws SecurityException  thrown if there is an error resolving the key from the KeyValue
     */
    protected void resolveKeyValue(KeyInfoResolutionContext kiContext, List<KeyValue> keyValues, 
            CredentialCriteriaSet criteriaSet)  throws SecurityException {
        
        for (KeyValue keyValue : keyValues) {
            for (KeyInfoProvider provider : providers) {
                if (! provider.handles(keyValue)) {
                    continue;
                }
                Collection<Credential> creds = provider.process(this, keyValue, criteriaSet, kiContext);
                if (creds != null && ! creds.isEmpty()) {
                    kiContext.setKeyValueCredential( creds.iterator().next() );
                    return;
                }
            }
        }
    }
    
    /**
     * Build a new credential context based on the KeyInfo being processed.
     * 
     * @param keyInfo the KeyInfo element being processed
     * @return a new KeyInfoCredentialContext 
     * @throws SecurityException if the new credential context could not be built
     */
    protected KeyInfoCredentialContext buildContext(KeyInfo keyInfo) throws SecurityException {
       KeyInfoCredentialContext context = (KeyInfoCredentialContext) newCredentialContext(); 
       context.setKeyInfo(keyInfo);
       return context;
    }
    
    /**
     * Build a BasicCredential consisting only of the values from KeyName.
     * 
     * @param keyNames the names to represent in the credential
     * @param keyInfo the KeyInfo context for the credential
     * @return a key name-only basic credential
     * @throws SecurityException  thrown if there is an error building the credential context
     */
    protected Credential buildKeyNameOnlyCredential(List<String> keyNames, KeyInfo keyInfo) throws SecurityException {
        BasicCredential basicCredential = new BasicCredential();
        basicCredential.getKeyNames().addAll(keyNames);
        basicCredential.setCredentialContext(buildContext(keyInfo));
        return basicCredential;
    }

    /**
     * Utility method to extract any key that might be present in the specified Credential.
     * 
     * @param cred the Credential to evaluate
     * @return the Key contained in the credential, or null if it does not contain a key.
     */
    protected Key extractKeyValue(Credential cred) {
        if (cred == null) {
            return null;
        }
        if (cred.getPublicKey() != null) {
            return cred.getPublicKey();
        } 
        // This could happen if key is derived, e.g. key agreement, etc
        if (cred.getSecretKey() != null) {
            return cred.getSecretKey();
        }
        // Perhaps unlikely, but go ahead and check
        if (cred.getPrivateKey() != null) {
            return cred.getPrivateKey(); 
        }
        return null;
    }
    
    /**
     *  Resolution context class that can be used to supply information to the providers within
     *  a given invocation of the resolver.
     */
    public class KeyInfoResolutionContext {
        
        /** List of key names. */
        private List<String> names;
        
        /** The credential resolved based on a KeValue, if any.  */
        private Credential credential;
        
        /** This list provides a KeyInfo provider plugin access to credentials that 
         * may have already been resolved by a plugin earlier in the chain. */
        private Collection<Credential> resolvedCredentials;
        
        /** Extensible map of properties. */
        private final Map<String, Object> properties;
        
        /**
         * Constructor.
         * 
         * @param credentials a reference to the collection in which the KeyInfo credential resolver 
         *          will store resolved credentials.
         */
        public KeyInfoResolutionContext(Collection<Credential> credentials) {
            //TODO should we make access to previously resolved creds modifiable by provider plugins?
            resolvedCredentials = Collections.unmodifiableCollection(credentials);
            properties = new HashMap<String, Object>();
        }

        /**
         * Get the names obtained from any KeyNames.
         * 
         * @return Returns the keyNames.
         */
        public List<String> getKeyNames() {
            return names;
        }

        /**
         * Set the names from any KeyNames.
         * 
         * @param keyNames The KeyNames to set.
         */
        public void setKeyNames(List<String> keyNames) {
            this.names = keyNames;
        }

        /**
         * Get the credential holding the key obtained from a KeyValue, if any.
         * 
         * @return Returns the keyValueCredential.
         */
        public Credential getKeyValueCredential() {
            return credential;
        }

        /**
         * Get the set of credentials already resolved by other providers.
         * 
         * @return Returns the keyValueCredential.
         */
        public Collection<Credential> getResolvedCredentials() {
            return resolvedCredentials;
        }
        
        /**
         * Set the credential holding the key obtained from a KeyValue, if any.
         * 
         * @param keyValueCredential The credential to set.
         */
        public void setKeyValueCredential(Credential keyValueCredential) {
            this.credential = keyValueCredential;
        }
        /**
         * Get the extensible properties map.
         * 
         * @return Returns the properties.
         */
        public Map<String, Object> getProperties() {
            return properties;
        }

    }

}