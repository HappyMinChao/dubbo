    /**
     * 加入多播组
     * 
     * @param multicastSocket 多播套接字
     * @param multicastAddress 多播地址
     * @throws IOException 如果发生I/O错误抛出IOException
     */
    @SuppressWarnings("deprecation")
    public static void joinMulticastGroup(MulticastSocket multicastSocket, InetAddress multicastAddress)
            throws IOException {
        setInterface(multicastSocket, multicastAddress instanceof Inet6Address);

        // For the deprecation notice: the equivalent only appears in JDK 9+.
        multicastSocket.setLoopbackMode(false);
        multicastSocket.joinGroup(multicastAddress);
    }

    /**
     * 设置多播套接字接口
     * 
     * @param multicastSocket 多播套接字
     * @param preferIpv6 是否优先使用IPv6
     * @throws IOException 如果发生I/O错误抛出IOException
     */
    @SuppressWarnings("deprecation")
    public static void setInterface(MulticastSocket multicastSocket, boolean preferIpv6) throws IOException {
        boolean interfaceSet = false;
        for (NetworkInterface networkInterface : getValidNetworkInterfaces()) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (preferIpv6 && address instanceof Inet6Address) {
                    try {
                        if (address.isReachable(100)) {
                            multicastSocket.setInterface(address);
                            interfaceSet = true;
                            break;
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                } else if (!preferIpv6 && address instanceof Inet4Address) {
                    try {
                        if (address.isReachable(100)) {
                            multicastSocket.setInterface(address);
                            interfaceSet = true;
                            break;
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            if (interfaceSet) {
                break;
            }
        }
    }

    /**
     * 检查地址是否与指定模式匹配，目前仅支持IPv4，对于IPv6地址请使用{@link this#matchIpExpression(String, String, int)}
     * Check if address matches with specified pattern, currently only supports ipv4, use {@link this#matchIpExpression(String, String, int)} for ipv6 addresses.
     *
     * @param pattern CIDR模式
     * @param address 'ip:port'格式的地址
     * @return 如果地址与模式匹配返回true，否则返回false
     * @throws UnknownHostException 如果主机未知抛出UnknownHostException
     */
    public static boolean matchIpExpression(String pattern, String address) throws UnknownHostException {
        if (address == null) {
            return false;
        }

        String host = address;
        int port = 0;
        // 仅适用于'ip:port'格式的IPv4地址
        if (address.endsWith(":")) {
            String[] hostPort = address.split(":");
            host = hostPort[0];
            port = StringUtils.parseInteger(hostPort[1]);
        }

        // 如果模式是子网格式，则不允许在模式中配置端口参数
        if (pattern.contains("/")) {
            CIDRUtils utils = new CIDRUtils(pattern);
            return utils.isInRange(host);
        }

        return matchIpRange(pattern, host, port);
    }

    /**
     * 检查主机和端口是否与指定模式匹配
     * 
     * @param pattern 模式
     * @param host 主机
     * @param port 端口
     * @return 如果匹配返回true，否则返回false
     * @throws UnknownHostException 如果主机未知抛出UnknownHostException
     */
    public static boolean matchIpExpression(String pattern, String host, int port) throws UnknownHostException {

        // 如果模式是子网格式，则不允许在模式中配置端口参数
        if (pattern.contains("/")) {
            CIDRUtils utils = new CIDRUtils(pattern);
            return utils.isInRange(host);
        }

        return matchIpRange(pattern, host, port);
    }

    /**
     * 检查IP范围是否匹配
     * 
     * @param pattern 模式
     * @param host 主机
     * @param port 端口
     * @return 如果匹配返回true，否则返回false
     * @throws UnknownHostException 如果主机未知抛出UnknownHostException
     */
    public static boolean matchIpRange(String pattern, String host, int port) throws UnknownHostException {
        if (pattern == null || host == null) {
            throw new IllegalArgumentException(
                    "Illegal Argument pattern or hostName. Pattern:" + pattern + ", Host:" + host);
        }
        pattern = pattern.trim();
        if ("*.*.*.*".equals(pattern) || "*".equals(pattern)) {
            return true;
        }

        InetAddress inetAddress = InetAddress.getByName(host);
        boolean isIpv4 = isValidV4Address(inetAddress);
        String[] hostAndPort = getPatternHostAndPort(pattern, isIpv4);
        if (hostAndPort[1] != null && !hostAndPort[1].equals(String.valueOf(port))) {
            return false;
        }
        pattern = hostAndPort[0];

        String splitCharacter = SPLIT_IPV4_CHARACTER;
        if (!isIpv4) {
            splitCharacter = SPLIT_IPV6_CHARACTER;
        }
        String[] mask = pattern.split(splitCharacter);
        // 检查模式格式
        checkHostPattern(pattern, mask, isIpv4);

        host = inetAddress.getHostAddress();
        if (pattern.equals(host)) {
            return true;
        }

        // 短名称条件
        if (!ipPatternContainExpression(pattern)) {
            InetAddress patternAddress = InetAddress.getByName(pattern);
            return patternAddress.getHostAddress().equals(host);
        }

        String[] ipAddress = host.split(splitCharacter);

        for (int i = 0; i < mask.length; i++) {
            if ("*".equals(mask[i]) || mask[i].equals(ipAddress[i])) {
                continue;
            } else if (mask[i].contains("-")) {
                String[] rangeNumStrs = StringUtils.split(mask[i], '-');
                if (rangeNumStrs.length != 2) {
                    throw new IllegalArgumentException("There is wrong format of ip Address: " + mask[i]);
                }
                Integer min = getNumOfIpSegment(rangeNumStrs[0], isIpv4);
                Integer max = getNumOfIpSegment(rangeNumStrs[1], isIpv4);
                Integer ip = getNumOfIpSegment(ipAddress[i], isIpv4);
                if (ip < min || ip > max) {
                    return false;
                }
            } else if ("0".equals(ipAddress[i])
                    && ("0".equals(mask[i])
                            || "00".equals(mask[i])
                            || "000".equals(mask[i])
                            || "0000".equals(mask[i]))) {
                continue;
            } else if (!mask[i].equals(ipAddress[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否为多播地址
     * is multicast address or not
     *
     * @param host IPv4地址
     * @return 如果是多播地址返回true
     */
    public static boolean isMulticastAddress(String host) {
        int i = host.indexOf('.');
        if (i > 0) {
            String prefix = host.substring(0, i);
            if (StringUtils.isNumber(prefix)) {
                int p = Integer.parseInt(prefix);
                return p >= 224 && p <= 239;
            }
        }
        return false;
    }

    /**
     * 检查IP模式是否包含表达式
     * 
     * @param pattern 模式
     * @return 如果包含表达式返回true，否则返回false
     */
    private static boolean ipPatternContainExpression(String pattern) {
        return pattern.contains("*") || pattern.contains("-");
    }

    /**
     * 检查主机模式
     * 
     * @param pattern 模式
     * @param mask 掩码数组
     * @param isIpv4 是否为IPv4
     */
    private static void checkHostPattern(String pattern, String[] mask, boolean isIpv4) {
        if (!isIpv4) {
            if (mask.length != 8 && ipPatternContainExpression(pattern)) {
                throw new IllegalArgumentException(
                        "If you config ip expression that contains '*' or '-', please fill qualified ip pattern like 234e:0:4567:0:0:0:3d:*. ");
            }
            if (mask.length != 8 && !pattern.contains("::")) {
                throw new IllegalArgumentException(
                        "The host is ipv6, but the pattern is not ipv6 pattern : " + pattern);
            }
        } else {
            if (mask.length != 4) {
                throw new IllegalArgumentException(
                        "The host is ipv4, but the pattern is not ipv4 pattern : " + pattern);
            }
        }
    }

    /**
     * 获取模式中的主机和端口
     * 
     * @param pattern 模式
     * @param isIpv4 是否为IPv4
     * @return 包含主机和端口的字符串数组
     */
    private static String[] getPatternHostAndPort(String pattern, boolean isIpv4) {
        String[] result = new String[2];
        if (pattern.startsWith("[") && pattern.contains("]:")) {
            int end = pattern.indexOf("]:");
            result[0] = pattern.substring(1, end);
            result[1] = pattern.substring(end + 2);
            return result;
        } else if (pattern.startsWith("[") && pattern.endsWith("]")) {
            result[0] = pattern.substring(1, pattern.length() - 1);
            result[1] = null;
            return result;
        } else if (isIpv4 && pattern.contains(":")) {
            int end = pattern.indexOf(":");
            result[0] = pattern.substring(0, end);
            result[1] = pattern.substring(end + 1);
            return result;
        } else {
            result[0] = pattern;
            return result;
        }
    }

    /**
     * 获取IP段的数值
     * 
     * @param ipSegment IP段
     * @param isIpv4 是否为IPv4
     * @return IP段的数值
     */
    private static Integer getNumOfIpSegment(String ipSegment, boolean isIpv4) {
        if (isIpv4) {
            return Integer.parseInt(ipSegment);
        }
        return Integer.parseInt(ipSegment, 16);
    }

    /**
     * 检查是否为标准格式的IPv6 URL
     * 
     * @param ip IP地址
     * @return 如果是标准格式的IPv6 URL返回true，否则返回false
     */
    public static boolean isIPV6URLStdFormat(String ip) {
        if ((ip.charAt(0) == '[' && ip.indexOf(']') > 2)) {
            return true;
        } else if (ip.indexOf(":") != ip.lastIndexOf(":")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取合法的IP地址
     * 
     * @param ip IP地址
     * @return 合法的IP地址
     */
    public static String getLegalIP(String ip) {
        // ipv6 [::FFFF:129.144.52.38]:80
        int ind;
        if ((ip.charAt(0) == '[' && (ind = ip.indexOf(']')) > 2)) {
            String nhost = ip;
            ip = nhost.substring(0, ind + 1);
            ip = ip.substring(1, ind);
            return ip;
        } else {
            return ip;
        }
    }