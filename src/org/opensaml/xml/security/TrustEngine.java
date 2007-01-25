/*
 * Copyright [2006] [University Corporation for Advanced Internet Development, Inc.]
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

package org.opensaml.xml.security;

import org.opensaml.xml.signature.KeyInfo;

/**
 * Evaluates the trustworthiness and validity of a token against implementation-specific requirements.
 * 
 * @param <TokenType> the token type this trust engine evaluates
 * @param <KeyInfoResolverType> KeyInfo information resolver type
 */
public interface TrustEngine<TokenType, KeyInfoResolverType extends KeyInfoResolver> {

    /**
     * Gets the default source of keying information to validate the token against.
     * 
     * @return default source of keying information to validate the token against
     */
    public KeyInfoSource getDefaultKeyInfoSource();

    /**
     * Sets the default source of keying information to validate the token against.
     * 
     * @param keyInfoSource default source of keying information to validate the token against
     */
    public void setDefaultkeyInfoSource(KeyInfoSource keyInfoSource);

    /**
     * Gets the default key resolver to use when extracting keying information from the {@link KeyInfoSource}.
     * 
     * @return default key resolver to use when extracting keying information from the {@link KeyInfoSource}
     */
    public KeyInfoResolverType getDefaultKeyResolver();

    /**
     * Sets the default key resolver to use when extracting keying information from the {@link KeyInfoSource}.
     * 
     * @param keyResolver default key resolver to use when extracting keying information from the {@link KeyInfoSource}
     */
    public void setDefaultKeyResolver(KeyInfoResolverType keyResolver);

    /**
     * Validates the token against the default key info using the default key resolver.
     * 
     * @param token the security token to validate
     * 
     * @return true if the token is trusted and valid, false if not
     * 
     * @throws SecurityException thrown if there is a problem validating the security token
     */
    public boolean validate(TokenType token) throws SecurityException;

    /**
     * Validates the token against the given key info using the default key resolver.
     * 
     * @param token the security token to validate
     * @param keyInfoSource source of keying information
     * @param keyResolver resolver used to extract keys from {@link KeyInfo}s
     * 
     * @return true if the token is trusted and valid, false if not
     * 
     * @throws SecurityException thrown if there is a problem validating the security token
     */
    public boolean validate(TokenType token, KeyInfoSource keyInfoSource, KeyInfoResolverType keyResolver)
            throws SecurityException;
}